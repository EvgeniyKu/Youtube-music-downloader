package evgeniy.kurinnoy.musicdownloader.ui.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import evgeniy.kurinnoy.musicdownloader.domain.MusicDownloadManager
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingState
import evgeniy.kurinnoy.musicdownloader.utils.LocalServiceBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService: Service() {
    private val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        handleException(throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val binder by lazy { LocalServiceBinder(this) }

    private val serviceNotificationManager by lazy {
        ServiceNotificationManager(this)
    }

    @Inject
    lateinit var downloadManager: MusicDownloadManager


    private val _error = MutableSharedFlow<Throwable>()
    val error = _error.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        serviceNotificationManager.createNotificationChannel()
        downloadManager.downloadingFiles
            .onEach(::onLoadingUpdate)
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD -> intent.getStringExtra(MUSIC_URL)?.let { url ->
                downloadMusic(url)
            }
            ACTION_REMOVE ->  intent.getStringExtra(MUSIC_URL)?.let { url ->
                scope.launch {
                    downloadManager.removeState(url)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    fun downloadMusic(youtubeUrl: String) {
        downloadManager.downloadBestAudioFromYoutube(scope, youtubeUrl)
    }

    fun tryAgain(info: MusicDownloadingState.Failure) {
        downloadManager.downloadFromYoutube(scope, info.url, info.info.musicInfo, info.info.selectedFormat)
    }

    fun cancel(id: String) {
        scope.launch {
            downloadManager.cancel(id)
        }
    }

    private fun onLoadingUpdate(list: List<MusicDownloadingState>) {
        serviceNotificationManager.updateNotifications(list)
    }

    private fun handleException(throwable: Throwable) {
        Log.e("DownloadService", "loading error", throwable)
        scope.launch {
            _error.emit(throwable)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    companion object {
        private const val ACTION_LOAD = "action_load"
        private const val ACTION_REMOVE = "action_remove"

        private const val MUSIC_URL = "music_url_extra"

        fun loadMusicIntent(context: Context, url: String): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_LOAD
                putExtra(MUSIC_URL, url)
            }
        }

        fun removeMusicIntent(context: Context, url: String): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_REMOVE
                putExtra(MUSIC_URL, url)
            }
        }
    }
}