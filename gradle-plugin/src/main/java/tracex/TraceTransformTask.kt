package tracex

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream

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

    @get:OutputDirectory
    abstract val intermediate: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun transform(inputChanges: InputChanges) {
        if (!inputChanges.isIncremental) {
            nonIncrementalTransform(
                allJars.get().map { it.asFile },
                allDirectories.get().map { it.asFile },
                intermediate.get().asFile,
            )
        } else {
            incrementalTransform(
                inputChanges.getFileChanges(allJarsFileCollection),
                inputChanges.getFileChanges(allDirectoriesFileCollection),
                intermediate.get().asFile,
            )
        }

        mergeClasses(
            intermediate.get().asFile,
            outputJar.get().asFile,
        )
    }

    private fun nonIncrementalTransform(
        allJars: Iterable<File>,
        allClasses: Iterable<File>,
        intermediate: File,
    ) {
        allJars.forEach { jar ->
            transformJar(
                jar,
                File(intermediate, jar.path.toSha256())
            )
        }

        allClasses.forEach { rootDir ->
            transformClasses(
                rootDir,
                File(intermediate, rootDir.path.toSha256())
            )
        }
    }

    private fun incrementalTransform(
        changedJars: Iterable<FileChange>,
        changedClasses: Iterable<FileChange>,
        intermediate: File,
    ) {
        changedJars.forEach { changedJar ->
            val jar = changedJar.file
            val outputDir = File(intermediate, jar.path.toSha256())

            when (changedJar.changeType) {
                ChangeType.ADDED -> {
                    transformJar(jar, outputDir)
                }

                ChangeType.MODIFIED -> {
                    outputDir.deleteRecursively()
                    transformJar(jar, outputDir)
                }

                ChangeType.REMOVED -> {
                    outputDir.deleteRecursively()
                }

                else -> {}
            }
        }

        changedClasses.forEach { changedRootDir ->
            val rootDir = changedRootDir.file
            val outputDir = File(intermediate, rootDir.path.toSha256())
            when (changedRootDir.changeType) {
                ChangeType.ADDED -> {
                    transformClasses(rootDir, outputDir)
                }

                ChangeType.MODIFIED -> {
                    outputDir.deleteRecursively()
                    transformClasses(rootDir, outputDir)
                }

                ChangeType.REMOVED -> {
                    outputDir.deleteRecursively()
                }

                else -> {}
            }
        }
    }


    private fun mergeClasses(
        intermediate: File,
        outputJar: File,
    ) {
        JarOutputStream(
            outputJar.outputStream()
                .buffered()
        ).use { jar ->
            intermediate.listFiles()?.forEach { rootDir ->
                rootDir.allFiles { child ->
                    if (child.isDirectory) return@allFiles
                    val name = child.toRelativeString(rootDir)
                    val entry = JarEntry(name)
                    jar.putNextEntry(entry)
                    child.inputStream().use { input -> input.transferTo(jar) }
                    jar.closeEntry()
                }
            }
        }
    }

    private fun transformJar(
        jar: File,
        outputDir: File,
    ) {
        JarInputStream(
            jar.inputStream()
                .buffered()
        ).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.isDirectory) continue
                if (!includeFileInTransform(entry.name)) continue
                val outputFile = File(outputDir, entry.name)
                    .also {
                        it.parentFile.mkdirs()
                        it.createNewFile()
                    }

                outputFile.outputStream()
                    .buffered()
                    .use { output ->
                        transform(input, output)
                    }
            }
        }
    }

    private fun transformClasses(
        rootDir: File,
        outputDir: File,
    ) {
        rootDir.allFiles { child ->
            if (child.isDirectory) return@allFiles
            val relativePath = child.toRelativeString(rootDir)
            if (!includeFileInTransform(relativePath)) return@allFiles
            val outputFile = File(outputDir, relativePath)
                .also {
                    it.parentFile.mkdirs()
                    it.createNewFile()
                }

            child.inputStream()
                .buffered()
                .use { input ->
                    outputFile.outputStream()
                        .buffered()
                        .use { output ->
                            transform(input, output)
                        }
                }
        }
    }

    private fun transform(
        input: InputStream,
        output: OutputStream,
    ) {
        val cr = ClassReader(input)
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cr.accept(TraceClassVisitor(Opcodes.ASM9, cw), ClassReader.EXPAND_FRAMES)
        output.write(cw.toByteArray())
    }

    private fun String.toSha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun includeFileInTransform(relativePath: String): Boolean {
        val lowerCase = relativePath.lowercase(Locale.ROOT)
        if (!lowerCase.endsWith(".class")) {
            return false
        }

        if (lowerCase == "module-info.class" ||
            lowerCase.endsWith("/module-info.class")
        ) {
            return false
        }

        if (lowerCase.startsWith("/meta-info/") ||
            lowerCase.startsWith("meta-info/")
        ) {
            return false
        }
        return true
    }

    private fun File.allFiles(block: (File) -> Unit) {
        if (this.isFile) {
            block(this)
            return
        }

        val children = listFiles() ?: return
        children.forEach { it.allFiles(block) }
    }
}