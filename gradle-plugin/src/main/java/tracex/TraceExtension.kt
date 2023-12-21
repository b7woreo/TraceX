package tracex

import com.android.build.api.dsl.ApplicationBuildType
import org.gradle.api.provider.Property

interface TraceExtension {
  val enable: Property<Boolean>

  companion object {
    internal fun create(buildType: ApplicationBuildType) {
      buildType.extensions.create("tracex", TraceExtension::class.java)
    }
  }

}