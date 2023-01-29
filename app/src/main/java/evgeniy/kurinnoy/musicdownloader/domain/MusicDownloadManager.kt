package evgeniy.kurinnoy.musicdownloader.domain

import evgeniy.kurinnoy.musicdownloader.data.FileManager
import evgeniy.kurinnoy.musicdownloader.data.MusicDataProvider
import evgeniy.kurinnoy.musicdownloader.data.PrefsManager
import evgeniy.kurinnoy.musicdownloader.data.models.DownloadableFile
import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo
import evgeniy.kurinnoy.musicdownloader.domain.exceptions.DiskAccessException
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingInfo
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class MusicDownloadManager @Inject constructor(
    private val prefsManager: PrefsManager,
    private val fileManager: FileManager,
    private val musicDataProvider: MusicDataProvider,
) {

    private val _downloadingFiles = MutableSharedFlow<List<MusicDownloadingState>>(replay = 1)
    val downloadingFiles = _downloadingFiles.asSharedFlow()

    private val downloadingJobs = Collections.synchronizedMap(mutableMapOf<String, Job>())

    private val updateStateMutex = Mutex()

    fun downloadBestAudioFromYoutube(scope: CoroutineScope, url: String) {
        if (isExist(url)) {
            return
        }
        downloadingJobs[url] = scope.launch {
            updateState(url) {
                MusicDownloadingState.Pending(url)
            }
            val musicInfo = try {
                musicDataProvider.loadVideoInfo(url)
            } catch (t: Throwable) {
                removeState(url)
                throw t
            }
            val downloadingInfo = MusicDownloadingInfo(musicInfo, musicInfo.bestFormat)
            if (downloadingInfo.isAlreadyDownloaded()) {
                updateState(url) {
                    MusicDownloadingState.AlreadyExist(downloadingInfo, url)
                }
            } else {
                downloadFromYoutubeInternal(url, downloadingInfo)
            }
        }
    }

    fun downloadFromYoutube(
        scope: CoroutineScope,
        url: String,
        musicInfo: MusicInfo,
        audioFormat: MusicInfo.AudioFormat,
    ) {
        downloadingJobs[url] = scope.launch {
            val downloadingInfo = MusicDownloadingInfo(musicInfo, audioFormat)
            downloadFromYoutubeInternal(url, downloadingInfo)
        }
    }

    @Throws(DiskAccessException::class)
    private suspend fun downloadFromYoutubeInternal(
        url: String,
        downloadingInfo: MusicDownloadingInfo,
    ) {

        updateState(url) {
            MusicDownloadingState.InProgress(
                downloadingInfo,
                0f,
                url
            )
        }

        SingleSongDownloader(
            url = url,
            downloadingInfo = downloadingInfo
        ).downloadMusic()
    }

    suspend fun cancel(id: String) {
        downloadingJobs[id]?.cancel()
        removeState(id)
    }

    suspend fun removeState(id: String) {
        updateStateMutex.withLock {
            val allStates = _downloadingFiles.value.toMutableList()
            allStates.removeIf { it.id == id }
            _downloadingFiles.emit(allStates)
        }
        downloadingJobs.remove(id)
    }

    private fun isExist(url: String): Boolean {
        val stateForUrl = downloadingFiles.value.firstOrNull { it.id == url }
        return stateForUrl != null && stateForUrl !is MusicDownloadingState.Failure
    }

    private suspend fun updateProgress(
        url: String,
        progress: Float,
    ) {
        updateState(url) {
            val currentProgress = (it as? MusicDownloadingState.InProgress)
                ?: return
            currentProgress.copy(progress = progress)
        }
    }

    private suspend inline fun updateState(
        id: String,
        update: (currentState: MusicDownloadingState) -> MusicDownloadingState,
    ) {
        coroutineContext.ensureActive()
        updateStateMutex.withLock {
            val allStates = _downloadingFiles.value.toMutableList()
            val currentStateIndex = allStates.indexOfFirst { it.id == id }
            val currentState = allStates.getOrNull(currentStateIndex)
                ?: MusicDownloadingState.Pending(id)
            if (currentStateIndex == -1) {
                allStates.add(update(currentState))
            } else {
                allStates[currentStateIndex] = update(currentState)
            }
            _downloadingFiles.emit(allStates)
        }
    }

    private suspend fun MusicDownloadingInfo.isAlreadyDownloaded(): Boolean {
        return fileManager.isExistInExternalDirectory(
            externalDirectory = prefsManager.requireMusicDirectory(),
            fileName = createMusicFileName()
        )
    }

    private val SharedFlow<List<MusicDownloadingState>>.value: List<MusicDownloadingState>
        get() = replayCache.lastOrNull() ?: emptyList()

    private inner class SingleSongDownloader(
        private val url: String,
        private val downloadingInfo: MusicDownloadingInfo,
    ) {

        suspend fun downloadMusic() {
            try {
                val musicFolder = prefsManager.requireMusicDirectory()

                val progressFlow = fileManager.downloadFile(
                    url = downloadingInfo.selectedFormat.downloadUrl,
                    fileName = downloadingInfo.createMusicFileName(),
                )

                progressFlow.collectLatest { downloadableFile ->
                    when (downloadableFile) {
                        is DownloadableFile.Progress -> updateProgress(
                            url = url,
                            progress = downloadableFile.progress
                        )
                        is DownloadableFile.Complete -> {
                            fileManager.copyFileToExternalStorage(downloadableFile.file, musicFolder)
                            updateState(url) {
                                MusicDownloadingState.Completed(downloadingInfo, url)
                            }
                        }
                    }
                }

            } catch (t: Throwable) {
                updateState(url) {
                    MusicDownloadingState.Failure(downloadingInfo, t, url)
                }
            }
        }
    }
}
