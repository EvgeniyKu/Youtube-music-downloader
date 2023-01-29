package evgeniy.kurinnoy.musicdownloader.utils

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

class LocalServiceBinder <T: Service>(val service: T) : Binder()

@Composable
inline fun <reified S : Service> rememberService(): State<S?> {
    val serviceState = remember { mutableStateOf<S?>(null) }
    val context = LocalContext.current

    val serviceConnection = remember {
        object : ServiceConnection {

            @Suppress("unchecked_cast")
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val localBinder = binder as LocalServiceBinder<S>
                serviceState.value = localBinder.service
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceState.value = null
            }
        }
    }

    if (serviceState.value == null) {
        SideEffect {
            context.bindService(
                Intent(context, S::class.java),
                serviceConnection,
                Context.BIND_IMPORTANT
            )
        }
    }

    DisposableEffect(true) {
        onDispose {
            context.unbindService(serviceConnection)
            serviceState.value = null
        }
    }

    return serviceState
}
