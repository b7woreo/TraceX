package tracex

import org.gradle.api.provider.Property

interface TraceExtension {
    val enable: Property<Boolean>
}

