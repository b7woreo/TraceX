package tracex

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

abstract class TraceTransformTask : DefaultTask() {

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun transform() {
        JarOutputStream(
            outputJar.get().asFile
                .outputStream()
                .buffered()
        ).use { output ->
            allJars.get().forEach { jar ->
                transformJar(output, jar.asFile)
            }

            allDirectories.get().forEach { dir ->
                transformClasses(output, dir.asFile)
            }
        }
    }

    private fun transformJar(
        output: JarOutputStream,
        jar: File,
    ) {
        JarInputStream(
            jar.inputStream()
                .buffered()
        ).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.isDirectory) continue
                if (!entry.name.endsWith(".class")) continue

                transform(entry.name, input, output)
                    .onSuccess { logger.debug("transform ${entry.name} from $jar") }
            }
        }
    }

    private fun transformClasses(
        output: JarOutputStream,
        rootDir: File,
    ) {
        rootDir.walk().forEach { child ->
            if (child.isDirectory) return@forEach
            if (!child.name.endsWith(".class")) return@forEach
            val name = child.toRelativeString(rootDir)

            child.inputStream()
                .buffered()
                .use { input ->
                    transform(name, input, output)
                        .onSuccess { logger.debug("transform $name from $rootDir") }
                }
        }
    }

    private fun transform(
        name: String,
        input: InputStream,
        output: JarOutputStream,
    ) = runCatching {
        val entry = ZipEntry(name)
        output.putNextEntry(entry)

        val cr = ClassReader(input)
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cr.accept(TraceClassVisitor(Opcodes.ASM9, cw), ClassReader.EXPAND_FRAMES)

        output.write(cw.toByteArray())
        output.closeEntry()
    }

}