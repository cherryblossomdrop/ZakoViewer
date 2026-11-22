package moe.zako.viewer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import moe.zako.viewer.core.permission.StoragePermissionHelper

/**
 * 监听生命周期 ON_RESUME 并重新检查存储权限的统一副作用组件
 *
 * 跨下载、设置、备份恢复及目录选择器统一复用，杜绝生命周期监听代码重复。
 * 内部通过 [rememberUpdatedState] 保护外部回调闭包不过期。
 *
 * @param onPermissionResult 当生命周期 ON_RESUME 触发时回调，包含当前是否已授权 (granted)，
 *                           以及是否属于从“未授权”到“已授权”的单向跃迁 (newlyGranted)
 */
@Composable
fun ObserveStoragePermissionOnResume(
    onPermissionResult: (granted: Boolean, newlyGranted: Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCallback = rememberUpdatedState(onPermissionResult)

    DisposableEffect(lifecycleOwner) {
        var lastGranted = StoragePermissionHelper.hasAllFilesAccess()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = StoragePermissionHelper.hasAllFilesAccess()
                val newlyGranted = !lastGranted && granted
                lastGranted = granted
                currentCallback.value(granted, newlyGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * 简易重载版本，仅关心当前是否已授予权限
 */
@Composable
fun ObserveStoragePermissionOnResume(
    onPermissionResult: (granted: Boolean) -> Unit
) {
    ObserveStoragePermissionOnResume { granted, _ ->
        onPermissionResult(granted)
    }
}
