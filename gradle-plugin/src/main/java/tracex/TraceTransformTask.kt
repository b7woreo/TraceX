package tracex

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.Deflater
import javax.inject.Inject


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

    @get:OutputDirectory
    abstract val intermediate: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun transform(inputChanges: InputChanges) {
        val incremental = inputChanges.isIncremental
        if (!incremental) {
            println("Full build mode")
        } else {
            println("Incremental build mode")
        }

        val workQueue = workerExecutor.noIsolation()
        val intermediate = intermediate.get().asFile

        val intermediateJars = intermediate.resolve("jars")
        val intermediateClasses = intermediate.resolve("classes")

        transformJars(
            inputChanges = inputChanges,
            intermediate = intermediateJars,
            workQueue = workQueue
        )

        transformClasses(
            inputChanges = inputChanges,
            intermediate = intermediateClasses,
            workQueue = workQueue
        )

        workQueue.await()

        mergeClasses(
            outputJar.get().asFile,
            intermediateClasses,
            *(intermediateJars.listFiles() ?: emptyArray())
        )
    }

    private fun transformJars(inputChanges: InputChanges, intermediate: File, workQueue: WorkQueue) {
        val (jarChanges, reprocessAll) = JarsIdentity(
            inputJars = allJarsFileCollection,
            inputChanges = inputChanges
        ).compute()

        if (reprocessAll) {
            intermediate.deleteRecursively()
        }

        jarChanges.forEach { changedJar ->
            workQueue.submit(TransformJar::class.java) {
                it.identity.set(changedJar.identity)
                it.source.set(changedJar.file)
                it.changeType.set(ChangeType.MODIFIED)
                it.intermediate.set(intermediate)
                it.loggable.set(inputChanges.isIncremental)
            }
        }
    }

    private fun transformClasses(inputChanges: InputChanges, intermediate: File, workQueue: WorkQueue) {
        val classChanges = inputChanges.getFileChanges(allDirectoriesFileCollection)
        classChanges.forEach { changedClass ->
            workQueue.submit(TransformClass::class.java) {
                it.normalizedPath.set(changedClass.normalizedPath)
                it.source.set(changedClass.file)
                it.changeType.set(changedClass.changeType)
                it.intermediate.set(intermediate)
                it.loggable.set(inputChanges.isIncremental)
            }
        }
    }

    private fun mergeClasses(
        outputJar: File,
        vararg classpath: File,
    ) {
        JarOutputStream(
            outputJar.outputStream()
                .buffered()
        ).use { jar ->
            jar.setLevel(Deflater.NO_COMPRESSION)
            classpath.forEach { rootDir ->
                rootDir.allFiles { child ->
                    val name = child.toRelativeString(rootDir)
                    val entry = JarEntry(name)
                    jar.putNextEntry(entry)
                    child.inputStream().use { input -> input.transferTo(jar) }
                    jar.closeEntry()
                }
            }
        }
    }

    abstract class Transform<T : Transform.Parameters> : WorkAction<T> {

        protected val source: File
            get() = parameters.source.get().asFile

        protected val changeType: ChangeType
            get() = parameters.changeType.get()

        protected val intermediate: File
            get() = parameters.intermediate.get().asFile

        protected val loggable: Boolean
            get() = parameters.loggable.get()

        protected abstract val destination: File

        protected abstract fun transform()

        final override fun execute() {
            if (loggable) {
                println("[$changeType] $source -> $destination")
            }

            when (changeType) {
                ChangeType.ADDED -> {
                    transform()
                }

                ChangeType.MODIFIED -> {
                    destination.deleteRecursively()
                    transform()
                }

                ChangeType.REMOVED -> {
                    destination.deleteRecursively()
                }

                else -> {}
            }
        }

        protected fun includeFileInTransform(relativePath: String): Boolean {
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

        protected fun transform(
            input: InputStream,
            output: OutputStream,
        ) {
            val cr = ClassReader(input)
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
            cr.accept(TraceClassVisitor(Opcodes.ASM9, cw), ClassReader.EXPAND_FRAMES)
            output.write(cw.toByteArray())
        }

        interface Parameters : WorkParameters {
            val source: RegularFileProperty
            val changeType: Property<ChangeType>
            val intermediate: DirectoryProperty
            val loggable: Property<Boolean>
        }
    }

    abstract class TransformJar : Transform<TransformJar.Parameters>() {
        val identity: String
            get() = parameters.identity.get()

        override val destination: File
            get() = File(intermediate, identity)

        override fun transform() {
            JarInputStream(
                source.inputStream().buffered()
            ).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!includeFileInTransform(entry.name)) continue
                    val outputFile = File(destination, entry.name)
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

        interface Parameters : Transform.Parameters {
            val identity: Property<String>
        }
    }

    abstract class TransformClass : Transform<TransformClass.Parameters>() {

        protected val normalizedPath: String
            get() = parameters.normalizedPath.get()

        override val destination: File
            get() = File(intermediate, normalizedPath)

        override fun transform() {
            if (!includeFileInTransform(normalizedPath)) return
            destination.parentFile.mkdirs()
            destination.createNewFile()

            source.inputStream()
                .buffered()
                .use { input ->
                    destination.outputStream()
                        .buffered()
                        .use { output ->
                            transform(input, output)
                        }
                }
        }

        interface Parameters : Transform.Parameters {
            val normalizedPath: Property<String>
        }
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