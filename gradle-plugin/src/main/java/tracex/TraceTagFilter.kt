package tracex

class TraceTagFilter(
    includes: List<String>,
    excludes: List<String>,
) : (String) -> Boolean {

    private val includes = includes.map { it.toRegex() }
    private val excludes = excludes.map { it.toRegex() }

    override fun invoke(traceTag: String): Boolean {
        return when {
            excludes.any { traceTag.matches(it) } -> false
            includes.any { traceTag.matches(it) } -> true
            else -> includes.isEmpty()
        }
    }
}