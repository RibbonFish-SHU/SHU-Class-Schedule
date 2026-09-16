package io.github.zmdld11.shuschedule.data.parser

import io.github.zmdld11.shuschedule.data.db.TimeSlot

/**
 * 上大节次默认作息（13 节制）。
 *
 * 1-4 节与晚间 9-10 节来自校历 PDF 确认值；5-8 与 11-13 为近年校历口径推算的近似值
 * （上大下午节次时间在 2020/2023 校历间有过调整），设置页可改，M3 接入。
 */
object TimeSlotDefaults {

    val all: List<TimeSlot> = listOf(
        TimeSlot(1, "08:00", "08:45"),
        TimeSlot(2, "08:55", "09:40"),
        TimeSlot(3, "10:00", "10:45"),
        TimeSlot(4, "10:55", "11:40"),
        TimeSlot(5, "13:00", "13:45"),
        TimeSlot(6, "13:55", "14:40"),
        TimeSlot(7, "15:00", "15:45"),
        TimeSlot(8, "15:55", "16:40"),
        TimeSlot(9, "18:00", "18:45"),
        TimeSlot(10, "18:55", "19:40"),
        TimeSlot(11, "19:50", "20:35"),
        TimeSlot(12, "20:45", "21:30"),
        TimeSlot(13, "21:40", "22:25"),
    )
}
