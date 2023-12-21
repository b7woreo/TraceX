package tracex

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import tracex

abstract class TracePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.findPlugin("com.android.application")
            ?: throw RuntimeException("apply plugin: com.android.application first")

        val androidExtension = project.extensions
            .getByType(ApplicationExtension::class.java)

        androidExtension.buildTypes.configureEach { buildType ->
            TraceExtension.create(buildType)
        }

        val componentsExtension = project.extensions
            .getByType(ApplicationAndroidComponentsExtension::class.java)

        componentsExtension.onVariants { variant ->
            val buildTypeName = variant.buildType ?: return@onVariants

            val traceExtension = androidExtension
                .buildTypes
                .getByName(buildTypeName)
                .tracex()

            if (!traceExtension.enable.getOrElse(false)) {
                return@onVariants
            }

            val traceTransformTask = project.tasks.register(
                "transform${variant.name.capitalized()}WithTraceX",
                TraceTransformTask::class.java
            ) {
                it.intermediate.set(
                    project.layout.buildDirectory.dir("intermediates/tracex/${variant.name}")
                )
            }

            variant.artifacts
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