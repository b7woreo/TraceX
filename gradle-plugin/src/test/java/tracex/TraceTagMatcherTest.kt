package tracex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraceTagMatcherTest {

    @Test
    fun `test isMatch() empty includes & empty excludes`() {
        val matcher = TraceTagMatcher(
            includes = emptyList(), excludes = emptyList()
        )
        assertTrue(matcher.isMatch("abc"))
    }

    @Test
    fun `test isMatch() not empty includes & empty excludes`() {
        val matcher = TraceTagMatcher(
            includes = listOf("abc"), excludes = emptyList()
        )
        assertFalse(matcher.isMatch("def"))
        assertTrue(matcher.isMatch("abc"))
    }

    @Test
    fun `test isMatch() empty includes & not empty excludes`() {
        val matcher = TraceTagMatcher(
            includes = emptyList(), excludes = listOf("abc")
        )
        assertTrue(matcher.isMatch("def"))
        assertFalse(matcher.isMatch("abc"))
    }

    @Test
    fun `test isMatch() not empty includes & not empty excludes`() {
        val matcher = TraceTagMatcher(
            includes = listOf("abc.*"), excludes = listOf("abcdef")
        )
        assertTrue(matcher.isMatch("abcabc"))
        assertFalse(matcher.isMatch("abcdef"))
    }

}