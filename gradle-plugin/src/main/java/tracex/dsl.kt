import com.android.build.api.dsl.ApplicationBuildType
import tracex.TraceExtension

interface TraceSpec {

    var enabled: Boolean

    fun include(vararg regex: String)

    fun exclude(vararg regex: String)

}

fun ApplicationBuildType.tracex(action: TraceSpec.() -> Unit) {
    tracex().action()
}

fun ApplicationBuildType.tracex(): TraceSpec {
    return extensions.getByName(TraceExtension.EXTENSION_NAME) as TraceSpec
}