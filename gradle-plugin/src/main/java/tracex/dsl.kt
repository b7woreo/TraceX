import com.android.build.api.dsl.ApplicationBuildType
import tracex.TraceExtension

fun ApplicationBuildType.tracex(action: TraceExtension.() -> Unit) {
  tracex().action()
}

fun ApplicationBuildType.tracex(): TraceExtension {
  return extensions.getByType(TraceExtension::class.java)
}
