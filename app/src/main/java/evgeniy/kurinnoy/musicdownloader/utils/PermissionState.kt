package evgeniy.kurinnoy.musicdownloader.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Creates a [MutablePermissionState] that is remembered across compositions.
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 *
 * @param permission the permission to control and observe.
 */
@Composable
fun rememberPermissionState(
    permission: String,
): PermissionState {

    val context = LocalContext.current
    val permissionState = remember(permission) {
        MutablePermissionState(permission, context, context.findActivity())
    }

    // Refresh the permission status when the lifecycle is resumed
    PermissionLifecycleCheckerEffect(permissionState)

    // Remember RequestPermission launcher and assign it to permissionState
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            permissionState.hasPermission = isGranted
            permissionState.permissionRequested = true
            permissionState.permissionRequestResult.value = isGranted
        }
    )
    DisposableEffect(permissionState, launcher) {
        permissionState.launcher = launcher
        onDispose {
            permissionState.launcher = null
        }
    }

    return permissionState
}

/**
 * Effect that updates the `hasPermission` state of a revoked [MutablePermissionState] permission
 * when the lifecycle gets called with [lifecycleEvent].
 */
@Composable
internal fun PermissionLifecycleCheckerEffect(
    permissionState: MutablePermissionState,
    lifecycleEvent: Lifecycle.Event = Lifecycle.Event.ON_RESUME,
) {
    // Check if the permission was granted when the lifecycle is resumed.
    // The user might've gone to the Settings screen and granted the permission.
    val permissionCheckerObserver = remember(permissionState) {
        LifecycleEventObserver { _, event ->
            if (event == lifecycleEvent) {
                // If the permission is revoked, check again.
                // We don't check if the permission was denied as that triggers a process restart.
                if (!permissionState.hasPermission) {
                    permissionState.refreshHasPermission()
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, permissionCheckerObserver) {
        lifecycle.addObserver(permissionCheckerObserver)
        onDispose { lifecycle.removeObserver(permissionCheckerObserver) }
    }
}

/**
 * A state object that can be hoisted to control and observe [permission] status changes.
 *
 * In most cases, this will be created via [rememberPermissionState].
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 */
@Stable
interface PermissionState {

    /**
     * The permission to control and observe.
     */
    val permission: String

    /**
     * When `true`, the user has granted the [permission].
     */
    val hasPermission: Boolean

    /**
     * When `true`, the user should be presented with a rationale.
     */
    val shouldShowRationale: Boolean

    /**
     * When `true`, the [permission] request has been done previously.
     */
    val permissionRequested: Boolean

    /**
     * Request the [permission] to the user.
     *
     * This should always be triggered from non-composable scope, for example, from a side-effect
     * or a non-composable callback. Otherwise, this will result in an IllegalStateException.
     *
     * This triggers a system dialog that asks the user to grant or revoke the permission.
     * Note that this dialog might not appear on the screen if the user doesn't want to be asked
     * again or has denied the permission multiple times.
     * This behavior varies depending on the Android level API.
     */
    fun launchPermissionRequest()

    /**
     * Request the [permission] to the user and await for result .
     *
     * This should always be triggered from non-composable scope, for example, from a side-effect
     * or a non-composable callback. Otherwise, this will result in an IllegalStateException.
     *
     * This triggers a system dialog that asks the user to grant or revoke the permission.
     * Note that this dialog might not appear on the screen if the user doesn't want to be asked
     * again or has denied the permission multiple times.
     * This behavior varies depending on the Android level API.
     *
     * @return true if permission is granted, false if permission denied
     */
    suspend fun requestPermission(): Boolean
}

/**
 * A mutable state object that can be used to control and observe permission status changes.
 *
 * In most cases, this will be created via [rememberPermissionState].
 *
 * @param permission the permission to control and observe.
 * @param context to check the status of the [permission].
 * @param activity to check if the user should be presented with a rationale for [permission].
 */
@Stable
internal class MutablePermissionState(
    override val permission: String,
    private val context: Context,
    private val activity: Activity,
) : PermissionState {

    private var requestPermissionAtLauncherSet: Boolean = false

    var launcher: ActivityResultLauncher<String>? = null
        set(value) {
            field = value
            if (requestPermissionAtLauncherSet) {
                launchPermissionRequest()
                requestPermissionAtLauncherSet = false
            }
        }

    internal val permissionRequestResult = MutableStateFlow<Boolean?>(null)

    private var _hasPermission by mutableStateOf(context.checkPermission(permission))
    override var hasPermission: Boolean
        internal set(value) {
            _hasPermission = value
            refreshShouldShowRationale()
        }
        get() = _hasPermission

    override var shouldShowRationale: Boolean by mutableStateOf(
        activity.shouldShowRationale(permission)
    )
        private set

    override var permissionRequested: Boolean by mutableStateOf(false)

    override fun launchPermissionRequest() {
        permissionRequestResult.value = null
        val launcher = launcher
        if (launcher != null) {
            launcher.launch(permission)
        } else {
            requestPermissionAtLauncherSet = true
        }
    }

    override suspend fun requestPermission(): Boolean {
        launchPermissionRequest()
        return permissionRequestResult.filterNotNull().first()
    }

    fun refreshHasPermission() {
        hasPermission = context.checkPermission(permission)
    }

    private fun refreshShouldShowRationale() {
        shouldShowRationale = activity.shouldShowRationale(permission)
    }
}


fun Activity.shouldShowRationale(permission: String): Boolean {
    return ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
}

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Can't find activity by using this context")
}

fun Context.checkPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
}