package tracex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraceTagFilterTest {

    @Test
    fun `test invoke() empty includes & empty excludes`() {
        val matcher = TraceTagFilter(
            includes = emptyList(), excludes = emptyList()
        )
        assertTrue(matcher.invoke("abc"))
    }

    @Test
    fun `test invoke() not empty includes & empty excludes`() {
        val matcher = TraceTagFilter(
            includes = listOf("abc"), excludes = emptyList()
        )
        assertFalse(matcher.invoke("def"))
        assertTrue(matcher.invoke("abc"))
    }

    @Test
    fun `test invoke() empty includes & not empty excludes`() {
        val matcher = TraceTagFilter(
            includes = emptyList(), excludes = listOf("abc")
        )
        assertTrue(matcher.invoke("def"))
        assertFalse(matcher.invoke("abc"))
    }

    @Test
    fun `test invoke() not empty includes & not empty excludes`() {
        val matcher = TraceTagFilter(
            includes = listOf("abc.*"), excludes = listOf("abcdef")
        )
        assertTrue(matcher.invoke("abcabc"))
        assertFalse(matcher.invoke("abcdef"))
    }

}