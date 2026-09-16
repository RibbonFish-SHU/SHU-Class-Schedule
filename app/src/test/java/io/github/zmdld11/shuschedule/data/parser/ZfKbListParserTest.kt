package io.github.zmdld11.shuschedule.data.parser

import io.github.zmdld11.shuschedule.data.db.CourseSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZfKbListParserTest {

    /** 字段口径照抄 jwxk 助手消费的真实接口结构 */
    private val fixture = """
    {
      "kbList": [
        {
          "kcmc": "数据结构", "kch": "CS1001", "jxbmc": "数据结构-01", "jxb_id": "9100001",
          "xm": "张三", "zcmc": "计算机学院", "cdmc": "BJ103", "cdlbmc": "教学楼", "xqumc": "宝山",
          "xqj": "1", "xqjmc": "星期一", "jc": "第1-2节", "jcs": "1-2", "zcd": "1-8周", "xf": "4"
        },
        {
          "kcmc": "数据结构", "kch": "CS1001", "jxbmc": "数据结构-01", "jxb_id": "9100001",
          "xm": "张三", "zcmc": "计算机学院", "cdmc": "实验楼404", "cdlbmc": "实验室",
          "xqj": "3", "xqjmc": "星期三", "jc": "第3-4节", "jcs": "3-4", "zcd": "2-16周(双)", "xf": "4"
        },
        {
          "kcmc": "数据结构", "kch": "CS1001", "jxbmc": "数据结构-01", "jxb_id": "9100001",
          "xm": "张三", "cdmc": "BJ103", "xqumc": "宝山",
          "xqj": "1", "jcs": "1-2", "zcd": "1-8周", "xf": "4"
        },
        {
          "kcmc": "大学英语", "kch": "EN2001", "jxbmc": "大学英语-03", "jxb_id": "9100002",
          "xm": "李四", "cdmc": "C201",
          "xqj": "5", "jc": "第10节", "jcs": "10", "zcd": "1,5,9,13周", "xf": "2"
        },
        {
          "kcmc": "坏记录-无星期", "kch": "X1", "jxb_id": "9100003",
          "jcs": "1-2", "zcd": "1周", "xf": "1"
        }
      ],
      "xsxx": {"XM": "测试学生"}
    }
    """.trimIndent()

    @Test
    fun groupsByClassAndCounts() {
        val courses = ZfKbListParser.parse(fixture)
        assertEquals(2, courses.size)
    }

    @Test
    fun multiSessionCourseAggregates() {
        val ds = ZfKbListParser.parse(fixture).first { it.name == "数据结构" }
        assertEquals("CS1001", ds.courseCode)
        assertEquals("9100001", ds.classId)
        assertEquals("数据结构-01", ds.className)
        assertEquals("4", ds.credit)
        // 第3条与前两条之一完全重复 → 去重后应只剩 2 个排课单元
        assertEquals(2, ds.sessions.size)

        val monday = ds.sessions.first { it.weekday == 1 }
        assertEquals(1, monday.startNode)
        assertEquals(2, monday.endNode)
        assertEquals("BJ103", monday.room)
        assertEquals("张三", monday.teacher)
        assertEquals("宝山", monday.campus)
        assertEquals((1..8).toList(), CourseSession.weeksOf(monday.weeksMask).sorted())

        val wednesday = ds.sessions.first { it.weekday == 3 }
        assertEquals("实验楼404", wednesday.room)
        assertEquals("", wednesday.campus)
        assertEquals((2..16 step 2).toList(), CourseSession.weeksOf(wednesday.weeksMask).sorted())
    }

    @Test
    fun singleNodeSessionFromJcs() {
        val en = ZfKbListParser.parse(fixture).first { it.name == "大学英语" }
        val s = en.sessions.single()
        assertEquals(5, s.weekday)
        assertEquals(10, s.startNode)
        assertEquals(10, s.endNode)
        assertEquals(listOf(1, 5, 9, 13), CourseSession.weeksOf(s.weeksMask).sorted())
    }

    @Test
    fun skipsRecordWithoutWeekday() {
        val names = ZfKbListParser.parse(fixture).map { it.name }
        assertTrue(names.none { it.startsWith("坏记录") })
    }

    @Test
    fun malformedJsonReturnsEmpty() {
        assertTrue(ZfKbListParser.parse("not json").isEmpty())
        assertTrue(ZfKbListParser.parse("""{"kbList": []}""").isEmpty())
    }

    /** 调课消解（#18）：同学期同课同星期同节次、周次有交集时保留周次更长（原教师）记录 */
    private val rescheduleFixture = """
    {
      "kbList": [
        {
          "kcmc": "计算机组成原理A(2)", "kch": "CS2002", "jxbmc": "计组-01", "jxb_id": "9200001",
          "xm": "骆祥峰", "cdmc": "D417", "xqumc": "宝山主区",
          "xqj": "3", "jcs": "7-8", "zcd": "1-16周", "xf": "4"
        },
        {
          "kcmc": "计算机组成原理A(2)", "kch": "CS2002", "jxbmc": "计组-01", "jxb_id": "9200001",
          "xm": "王欣芝", "cdmc": "D417", "xqumc": "宝山主区",
          "xqj": "3", "jcs": "7-8", "zcd": "1-16周", "xf": "4"
        },
        {
          "kcmc": "计算机组成原理A(2)", "kch": "CS2002", "jxbmc": "计组-01", "jxb_id": "9200001",
          "xm": "王欣芝", "cdmc": "D417", "xqumc": "宝山主区",
          "xqj": "5", "jcs": "3-4", "zcd": "6周", "xf": "4"
        },
        {
          "kcmc": "计算机组成原理A(2)", "kch": "CS2002", "jxbmc": "计组-01", "jxb_id": "9200001",
          "xm": "骆祥峰", "cdmc": "机房301", "xqumc": "宝山主区",
          "xqj": "5", "jcs": "3-4", "zcd": "1-16周", "xf": "4"
        },
        {
          "kcmc": "计算机组成原理A(2)", "kch": "CS2002", "jxbmc": "计组-01", "jxb_id": "9200001",
          "xm": "王欣芝", "cdmc": "机房302", "xqumc": "宝山主区",
          "xqj": "5", "jcs": "3-4", "zcd": "17-18周", "xf": "4"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun keepsOriginalTeacherOnOverlappingConflict() {
        val course = ZfKbListParser.parse(rescheduleFixture).single()
        // 周三7-8：原教师 1-16周 与 现教师 1-16周 完全重叠 → 保留先出现的原教师
        val wed = course.sessions.filter { it.weekday == 3 }
        assertEquals(1, wed.size)
        assertEquals("骆祥峰", wed[0].teacher)

        // 周五3-4：调课单周(6周)先到，原教师 1-16周 后到但覆盖更长 → 替换之；
        // 17-18周 与 1-16周 无交集（合法分段）→ 共存
        val fri = course.sessions.filter { it.weekday == 5 }.sortedBy { CourseSession.weeksOf(it.weeksMask).min() }
        assertEquals(2, fri.size)
        assertEquals("骆祥峰", fri[0].teacher)
        assertEquals(CourseSession.maskOf((1..16).toList()), fri[0].weeksMask)
        assertEquals("王欣芝", fri[1].teacher)
    }
}
