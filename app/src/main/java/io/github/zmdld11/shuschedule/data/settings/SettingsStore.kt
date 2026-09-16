package io.github.zmdld11.shuschedule.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val showOffWeekKey = booleanPreferencesKey("show_off_week")

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[dynamicColorKey] ?: true }

    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[dynamicColorKey] = value }
    }

    /** 周视图是否置灰显示非本周课程（默认隐藏） */
    val showOffWeek: Flow<Boolean> = context.dataStore.data.map { it[showOffWeekKey] ?: false }

    suspend fun setShowOffWeek(value: Boolean) {
        context.dataStore.edit { it[showOffWeekKey] = value }
    }
}
