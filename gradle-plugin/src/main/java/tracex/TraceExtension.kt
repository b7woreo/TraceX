package tracex

import TraceSpec
import com.android.build.api.dsl.ApplicationBuildType

abstract class TraceExtension : TraceSpec {

    internal val includes = mutableListOf<String>()
    internal val excludes = mutableListOf<String>()

    override var enabled: Boolean = false

    override fun include(vararg regex: String) {
        includes.addAll(regex)
    }

    override fun exclude(vararg regex: String) {
        excludes.addAll(regex)
    }

    companion object {

        internal const val EXTENSION_NAME = "tracex"

        internal fun ApplicationBuildType.createTraceExtension() {
            extensions.create(TraceSpec::class.java, EXTENSION_NAME, TraceExtension::class.java)
        }

    }
}


