package io.github.zmdld11.shuschedule.di

import android.content.Context
import androidx.room.Room
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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShuScheduleDatabase =
        Room.databaseBuilder(context, ShuScheduleDatabase::class.java, "shu_schedule.db")
            // 0.1.x 开发期 schema 允许破坏性变更，正式发布前改为迁移
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun provideSemesterDao(db: ShuScheduleDatabase): SemesterDao = db.semesterDao()

    @Provides fun provideCourseDao(db: ShuScheduleDatabase): CourseDao = db.courseDao()

    @Provides fun provideTimeSlotDao(db: ShuScheduleDatabase): TimeSlotDao = db.timeSlotDao()
}
