package io.github.zmdld11.shuschedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 应用级 UI 状态（主题等），Activity 层持有；打开 App 顺带刷新小组件 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsStore,
    widgetUpdater: io.github.zmdld11.shuschedule.widget.WidgetUpdater,
) : ViewModel() {

    init {
        widgetUpdater.pushAll()
    }

    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(value) }
    }
}
