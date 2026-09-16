package io.github.zmdld11.shuschedule.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlotDefaultsTest {

    @Test
    fun `12 slots numbered 1 to 12`() {
        assertEquals(12, TimeSlotDefaults.all.size)
        assertEquals((1..12).toList(), TimeSlotDefaults.all.map { it.node })
    }

    @Test
    fun `every slot lasts 45 minutes`() {
        TimeSlotDefaults.all.forEach { slot ->
            val start = slot.startTime.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
            val end = slot.endTime.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
            assertEquals("node ${slot.node}", 45, end - start)
        }
        assertTrue(TimeSlotDefaults.all.all { it.startTime < it.endTime })
    }
}
