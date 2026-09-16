package io.github.zmdld11.shuschedule.data.jwxk

import io.github.zmdld11.shuschedule.data.db.TermType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterOptionsParserTest {

    @Test
    fun `parses normal harvest envelope`() {
        val text = """
            {"ok":true,"years":[
               {"value":"2025","label":"2025","selected":false},
               {"value":"2026","label":"2026","selected":true}
             ],"terms":[
               {"value":"3","label":"秋季","selected":false},
               {"value":"12","label":"春季","selected":true},
               {"value":"32","label":"夏季","selected":false}
             ]}
        """.trimIndent()
        val choices = SemesterOptionsParser.parse(text)!!
        assertEquals(listOf(2025, 2026), choices.years.map { it.value })
        assertEquals(3, choices.terms.size)
        assertTrue(choices.years[1].selected)
        assertEquals(TermType.SPRING, SemesterOptionsParser.termTypeOf(choices.terms[1]))
        assertEquals(TermType.SUMMER, SemesterOptionsParser.termTypeOf(choices.terms[2]))
    }

    @Test
    fun `drops placeholder options with blank value`() {
        val text = """
            {"ok":true,"years":[{"value":"","label":"","selected":true},{"value":"2026","label":"2026","selected":false}],
             "terms":[{"value":"","label":""},{"value":"3","label":"秋季"}]}
        """.trimIndent()
        val choices = SemesterOptionsParser.parse(text)!!
        assertEquals(listOf(2026), choices.years.map { it.value })
        assertEquals(listOf(3), choices.terms.map { it.value })
    }

    @Test
    fun `null when not ok or empty lists or malformed`() {
        assertNull(SemesterOptionsParser.parse("""{"ok":false}"""))
        assertNull(SemesterOptionsParser.parse("""{"ok":true,"years":[],"terms":[{"value":"3","label":"秋"}]}"""))
        assertNull(SemesterOptionsParser.parse("""not json"""))
        assertNull(SemesterOptionsParser.parse("""{"ok":true,"years":"x","terms":[]}"""))
    }

    @Test
    fun `termTypeOf falls back to code map when label lacks season word`() {
        assertEquals(TermType.AUTUMN, SemesterOptionsParser.termTypeOf(SemesterOption(3, "第一学期", false)))
        assertEquals(TermType.SUMMER, SemesterOptionsParser.termTypeOf(SemesterOption(32, "3", false)))
        assertNull(SemesterOptionsParser.termTypeOf(SemesterOption(99, "第X学期", false)))
    }
}
