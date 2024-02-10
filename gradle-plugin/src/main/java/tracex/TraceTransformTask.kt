package tracex

import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipArchive
import com.google.common.hash.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale
import java.util.zip.Deflater


@CacheableTask
abstract class TraceTransformTask : DefaultTask() {

    @get:Internal
    abstract val allJars: ListProperty<RegularFile>

    @get:Internal
    abstract val allDirectories: ListProperty<Directory>

    @get:InputFiles
    @get:Classpath
    @get:Incremental
    val allJarsFileCollection: ConfigurableFileCollection by lazy {
        project.files(allJars)
    }

    @get:InputFiles
    @get:Classpath
    @get:Incremental
    val allDirectoriesFileCollection: ConfigurableFileCollection by lazy {
        project.files(allDirectories)
    }

    @get:Input
    abstract val includes: ListProperty<String>

    @get:Input
    abstract val excludes: ListProperty<String>

    @get:OutputDirectory
    abstract val intermediateDir: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun transformClasses(inputChanges: InputChanges) = runBlocking {
        val intermediateDir = intermediateDir.asFile.get()

        val allChangedClasses = timeLog("Compute changed classes cost") {
            val jarsChangedClasses = getJarsChangedClasses(
                jars = allJarsFileCollection,
                previousHashFile = intermediateDir.resolve("jar-hashes"),
            )
            val directoriesChangedClasses = getDirectoriesChangedClasses(
                directories = allDirectoriesFileCollection,
                inputChanges = inputChanges,
            )
            (jarsChangedClasses + directoriesChangedClasses)
                .also {
                    println("Added classes: ${it.count { it.changeType == ChangeType.ADDED }}")
                    println("Modified classes: ${it.count { it.changeType == ChangeType.MODIFIED }}")
                    println("Removed classes: ${it.count { it.changeType == ChangeType.REMOVED }}")
                }
        }

        val transformedClasses = timeLog("Transform classes cost") {
            transformClasses(
                classes = allChangedClasses,
                intermediateDir.resolve("classes"),
            )
        }

        timeLog("Merge classes cost") {
            mergeClasses(transformedClasses, outputJar.asFile.get())
        }
    }

    private suspend fun getJarsChangedClasses(
        jars: FileCollection,
        previousHashFile: File,
    ): List<ClassInfo> = coroutineScope {
        val previousContentHash = runCatching {
            ObjectInputStream(previousHashFile.inputStream().buffered()).use {
                it.readObject() as Map<String, String>
            }
        }.getOrElse { emptyMap() }

        val (currentContentHash, entries) = jars
            .map { jarFile ->
                val jarPath = jarFile.toPath()

                async(Dispatchers.Default) {
                    ZipArchive(jarPath).use { zip ->
                        zip.listEntries()
                            .map {
                                val hash = Hashing.sha256()
                                    .hashBytes(zip.getContent(it).array())
                                    .toString()
                                it to (hash to jarPath)
                            }
                    }
                }
            }
            .flatMap { it.await() }
            .associate { it }
            .let {
                it.mapValues { (_, value) -> value.first } to it.mapValues { (_, value) -> value.second }
            }

        ObjectOutputStream(previousHashFile.outputStream().buffered()).use {
            it.writeObject(currentContentHash)
        }

        val maybeModified = currentContentHash.keys.intersect(previousContentHash.keys)
        val added = currentContentHash.filterKeys { !maybeModified.contains(it) }.keys
        val removed = previousContentHash.filterKeys { !maybeModified.contains(it) }.keys
        val modified = maybeModified.filter { currentContentHash[it] != previousContentHash[it] }

        fun content(name: String): () -> ByteArray {
            return { ZipArchive(entries[name]).use { zip -> zip.getContent(name).array() } }
        }

        added.map {
            ClassInfo(
                name = it,
                changeType = ChangeType.ADDED,
                content = content(it)
            )
        } + modified.map {
            ClassInfo(
                name = it,
                changeType = ChangeType.MODIFIED,
                content = content(it)
            )
        } + removed.map {
            ClassInfo(
                name = it,
                changeType = ChangeType.REMOVED,
                content = { throw IllegalStateException() }
            )
        }
    }

    private fun getDirectoriesChangedClasses(
        directories: FileCollection,
        inputChanges: InputChanges,
    ): List<ClassInfo> {
        return inputChanges.getFileChanges(directories)
            .map {
                ClassInfo(
                    name = it.normalizedPath,
                    changeType = it.changeType,
                    content = {
                        if (it.changeType == ChangeType.REMOVED) throw IllegalStateException()
                        else it.file.readBytes()
                    }
                )
            }
    }

    private suspend fun transformClasses(classes: List<ClassInfo>, outputDir: File): List<ClassInfo> = coroutineScope {
        val filter = TraceTagFilter(
            includes = includes.get(),
            excludes = excludes.get()
        )

        fun doTransform(input: ByteArray): ByteArray {
            val cr = ClassReader(input)
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
            cr.accept(TraceClassVisitor(filter, Opcodes.ASM9, cw), ClassReader.EXPAND_FRAMES)
            return cw.toByteArray()
        }

        classes
            .filter { it.canTransform }
            .map { changedClass ->
                async(Dispatchers.Default) {
                    val outputFile = outputDir.resolve(changedClass.name)

                    when (changedClass.changeType) {
                        ChangeType.MODIFIED,
                        ChangeType.REMOVED -> {
                            outputFile.delete()
                        }

                        else -> {}
                    }

                    when (changedClass.changeType) {
                        ChangeType.ADDED,
                        ChangeType.MODIFIED -> {
                            outputFile.parentFile.mkdirs()
                            withContext(Dispatchers.IO) { outputFile.createNewFile() }
                            outputFile.writeBytes(doTransform(changedClass.content()))
                        }

                        else -> {}
                    }

                    changedClass.copy(
                        content = {
                            if (changedClass.changeType == ChangeType.REMOVED) throw IllegalStateException()
                            else outputFile.readBytes()
                        }
                    )
                }
            }.awaitAll()
    }

    private fun mergeClasses(classes: List<ClassInfo>, outputJar: File) {
        ZipArchive(outputJar.toPath()).use { zip ->
            classes.forEach {
                when (it.changeType) {
                    ChangeType.MODIFIED,
                    ChangeType.REMOVED -> {
                        zip.delete(it.name)
                    }

                    else -> {}
                }
            }

            classes.forEach {
                when (it.changeType) {
                    ChangeType.ADDED,
                    ChangeType.MODIFIED -> {
                        zip.add(BytesSource(it.content(), it.name, Deflater.NO_COMPRESSION))

                    }

                    else -> {}
                }
            }
        }
    }

    private data class ClassInfo(
        val name: String,
        val changeType: ChangeType,
        val content: () -> ByteArray,
    ) {
        val canTransform: Boolean
            get() {
                val lowerCase = name.lowercase(Locale.ROOT)
                if (!lowerCase.endsWith(".class")) {
                    return false
                }

                if (lowerCase == "module-info.class" || lowerCase.endsWith("/module-info.class")) {
                    return false
                }

                return !(lowerCase.startsWith("/meta-info/") || lowerCase.startsWith("meta-info/"))
            }
    }

    private inline fun <T> timeLog(message: String, block: () -> T): T {
        val startTimestamp = System.currentTimeMillis()
        val result = block()
        println("$message: ${System.currentTimeMillis() - startTimestamp}")
        return result
    }
}