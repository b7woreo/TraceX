package tracex

class TraceTagMatcher(
    includes: List<String>,
    excludes: List<String>,
) {

    private val includes = includes.map { it.toRegex() }
    private val excludes = excludes.map { it.toRegex() }

    fun isMatch(traceTag: String): Boolean {
        return when {
            excludes.any { traceTag.matches(it) } -> false
            includes.any { traceTag.matches(it) } -> true
            else -> includes.isEmpty()
        }
    }
}