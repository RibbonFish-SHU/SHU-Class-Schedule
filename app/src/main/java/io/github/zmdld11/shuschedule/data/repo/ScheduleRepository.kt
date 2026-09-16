package io.github.zmdld11.shuschedule.data.repo

import androidx.room.withTransaction
import io.github.zmdld11.shuschedule.data.backup.BackupCodec
import io.github.zmdld11.shuschedule.data.db.Course
import io.github.zmdld11.shuschedule.data.db.CourseDao
import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.db.CourseWithSessions
import io.github.zmdld11.shuschedule.data.db.Semester
import io.github.zmdld11.shuschedule.data.db.SemesterDao
import io.github.zmdld11.shuschedule.data.db.ShuScheduleDatabase
import io.github.zmdld11.shuschedule.data.db.TermType
import io.github.zmdld11.shuschedule.data.db.TimeSlot
import io.github.zmdld11.shuschedule.data.db.TimeSlotDao
import io.github.zmdld11.shuschedule.data.parser.ParsedCourse
import io.github.zmdld11.shuschedule.data.parser.TimeSlotDefaults
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 课表色板数量（colorIndex 对 8 取模分配，保证同名课跨学期颜色一致由 hash 决定） */
const val COURSE_PALETTE_SIZE = 8

@Singleton
class ScheduleRepository @Inject constructor(
    private val db: ShuScheduleDatabase,
    private val semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    private val timeSlotDao: TimeSlotDao,
) {

    fun observeSemesters(): Flow<List<Semester>> = semesterDao.observeAll()

    fun observeActiveSemester(): Flow<Semester?> = semesterDao.observeActive()

    fun observeCourses(semesterId: Long): Flow<List<CourseWithSessions>> =
        courseDao.observeSemesterCourses(semesterId)

    fun observeTimeSlots(): Flow<List<TimeSlot>> = timeSlotDao.observeAll()

    suspend fun getSemesterCourses(semesterId: Long): List<CourseWithSessions> =
        courseDao.getSemesterCourses(semesterId)

    suspend fun activeSemester(): Semester? = semesterDao.getActive()

    suspend fun timeSlots(): List<TimeSlot> = timeSlotDao.getAll()

    suspend fun activateSemester(id: Long) = semesterDao.activate(id)

    suspend fun updateSemesterRange(id: Long, startDateEpochDay: Long, totalWeeks: Int) =
        semesterDao.updateRange(id, startDateEpochDay, totalWeeks)

    suspend fun deleteSemester(id: Long) = semesterDao.delete(id)

    suspend fun upsertTimeSlot(slot: TimeSlot) = timeSlotDao.upsertAll(listOf(slot))

    /** 全量快照（备份导出用） */
    suspend fun backupSnapshot(): BackupCodec.Snapshot = db.withTransaction {
        val snapshot = mutableListOf<Pair<Semester, List<Pair<Course, List<CourseSession>>>>>()
        for (semester in semesterDao.getAll()) {
            val courses = courseDao.getSemesterCourses(semester.id).map { c ->
                c.course to c.sessions
            }
            snapshot += semester to courses
        }
        BackupCodec.Snapshot(semesters = snapshot, timeSlots = timeSlotDao.getAll())
    }

    /** 从备份恢复（整库重建式写入，保留备份中的激活学期标记） */
    suspend fun restoreBackup(snapshot: BackupCodec.Snapshot) = db.withTransaction {
        val activeKey = snapshot.semesters
            .firstOrNull { (s, _) -> s.isActive }
            ?.let { (s, _) -> s.year to s.term }
        snapshot.semesters.forEach { (semester, courses) ->
            val existing = semesterDao.findByYearTerm(semester.year, semester.term)
            val semesterId = if (existing != null) {
                semesterDao.updateRange(existing.id, semester.startDateEpochDay, semester.totalWeeks)
                existing.id
            } else {
                semesterDao.upsert(
                    Semester(
                        year = semester.year,
                        term = semester.term,
                        startDateEpochDay = semester.startDateEpochDay,
                        totalWeeks = semester.totalWeeks,
                    )
                )
            }
            courseDao.deleteBySemester(semesterId)
            courses.forEach { (course, sessions) ->
                val courseId = courseDao.insertCourses(listOf(course.copy(semesterId = semesterId))).first()
                courseDao.insertSessions(sessions.map { it.copy(courseId = courseId) })
            }
            if (timeSlotDao.getAll().isEmpty() && snapshot.timeSlots.isNotEmpty()) {
                timeSlotDao.upsertAll(snapshot.timeSlots)
            }
        }
        if (activeKey != null) {
            val current = semesterDao.getAll().firstOrNull { it.isActive }
            if (current == null) {
                semesterDao.findByYearTerm(activeKey.first, activeKey.second)?.let { activateSemester(it.id) }
            }
        }
    }

    /**
     * 导入解析后的课表：按 (year, term) 复用或新建学期，整体替换该学期的课程。
     * 首次导入自动设为激活学期并补默认节次作息。
     */
    suspend fun importParsed(
        year: Int,
        term: TermType,
        startDateEpochDay: Long,
        totalWeeks: Int,
        parsed: List<ParsedCourse>,
    ): Long = db.withTransaction {
        val existing = semesterDao.findByYearTerm(year, term)
        val semesterId = if (existing != null) {
            semesterDao.updateRange(existing.id, startDateEpochDay, totalWeeks)
            existing.id
        } else {
            semesterDao.upsert(
                Semester(
                    year = year,
                    term = term,
                    startDateEpochDay = startDateEpochDay,
                    totalWeeks = totalWeeks,
                )
            )
        }

        courseDao.deleteBySemester(semesterId)
        parsed.forEach { p ->
            val courseId = courseDao.insertCourses(
                listOf(
                    Course(
                        semesterId = semesterId,
                        name = p.name,
                        courseCode = p.courseCode,
                        className = p.className,
                        classId = p.classId,
                        credit = p.credit,
                        colorIndex = ((p.name.hashCode() % COURSE_PALETTE_SIZE) + COURSE_PALETTE_SIZE) % COURSE_PALETTE_SIZE,
                    )
                )
            ).first()
            courseDao.insertSessions(
                p.sessions.map {
                    CourseSession(
                        courseId = courseId,
                        weekday = it.weekday,
                        startNode = it.startNode,
                        endNode = it.endNode,
                        weeksMask = it.weeksMask,
                        room = it.room,
                        teacher = it.teacher,
                        campus = it.campus,
                    )
                }
            )
        }

        if (timeSlotDao.getAll().isEmpty()) timeSlotDao.upsertAll(TimeSlotDefaults.all)

        if (!semesterDao.hasActive()) semesterDao.activate(semesterId)

        semesterId
    }
}
