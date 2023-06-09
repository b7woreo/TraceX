package tracex

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized

abstract class TraceXPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions
            .findByType(ApplicationAndroidComponentsExtension::class.java)
            ?: throw RuntimeException("apply plugin: com.android.application first")
        extension.onVariants {
            val traceTransformTask = project.tasks.register(
                "transform${it.name.capitalized()}TraceX",
                TraceTransformTask::class.java
            )

            it.artifacts
                .forScope(ScopedArtifacts.Scope.ALL)
                .use(traceTransformTask)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    TraceTransformTask::allJars,
                    TraceTransformTask::allDirectories,
                    TraceTransformTask::outputJar
                )
        }
    }

}