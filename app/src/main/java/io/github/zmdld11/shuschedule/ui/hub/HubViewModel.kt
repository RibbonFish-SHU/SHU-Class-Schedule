package io.github.zmdld11.shuschedule.ui.hub

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.backup.BackupCodec
import io.github.zmdld11.shuschedule.data.repo.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HubViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {

    /** 备份导出到 SAF 文档 */
    fun exportTo(context: Context, uri: Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(BackupCodec.encode(repository.backupSnapshot()).toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            onResult(ok)
        }
    }

    /** 从 SAF 文档恢复备份 */
    fun importFrom(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取文件")
                    val snapshot = BackupCodec.decode(text) ?: error("备份文件格式不正确")
                    repository.restoreBackup(snapshot)
                    true to "恢复完成"
                }.getOrElse { e -> false to (e.message ?: "恢复失败") }
            }
            onResult(ok, msg)
        }
    }
}
