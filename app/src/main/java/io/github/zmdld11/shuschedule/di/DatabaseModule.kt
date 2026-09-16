package io.github.zmdld11.shuschedule.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.zmdld11.shuschedule.data.db.CourseDao
import io.github.zmdld11.shuschedule.data.db.SemesterDao
import io.github.zmdld11.shuschedule.data.db.ShuScheduleDatabase
import io.github.zmdld11.shuschedule.data.db.TimeSlotDao
import javax.inject.Singleton

/** v2→v3：调课标记列（老数据一律 false，重新导入后由解析器打标）。
 * 注意须为顶层命名类：@Module 内的匿名 object 字段会触发 Dagger KSP 校验异常。 */
private class Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE course_sessions ADD COLUMN rescheduled INTEGER NOT NULL DEFAULT 0")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShuScheduleDatabase =
        Room.databaseBuilder(context, ShuScheduleDatabase::class.java, "shu_schedule.db")
            .addMigrations(Migration2To3())
            // 开发期兜底（已提供 v2→v3 迁移，正常升级不触发破坏性重建）
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun provideSemesterDao(db: ShuScheduleDatabase): SemesterDao = db.semesterDao()

    @Provides fun provideCourseDao(db: ShuScheduleDatabase): CourseDao = db.courseDao()

    @Provides fun provideTimeSlotDao(db: ShuScheduleDatabase): TimeSlotDao = db.timeSlotDao()
}
