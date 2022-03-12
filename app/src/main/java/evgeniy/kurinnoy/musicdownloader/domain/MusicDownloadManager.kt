package evgeniy.kurinnoy.musicdownloader.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import evgeniy.kurinnoy.musicdownloader.data.FileManager
import evgeniy.kurinnoy.musicdownloader.data.MusicDataProvider
import evgeniy.kurinnoy.musicdownloader.data.PrefsManager
import evgeniy.kurinnoy.musicdownloader.data.models.DownloadableFile
import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo
import evgeniy.kurinnoy.musicdownloader.domain.exceptions.DiskAccessException
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingInfo
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingState
import evgeniy.kurinnoy.musicdownloader.utils.extension.copyTo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class MusicDownloadManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val prefsManager: PrefsManager,
    private val fileManager: FileManager,
    private val musicDataProvider: MusicDataProvider,
) {

    private val _downloadingFiles = MutableStateFlow<List<MusicDownloadingState>>(emptyList())
    val downloadingFiles = _downloadingFiles.asStateFlow()

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
            val audioFormat = musicInfo.bestFormat

            downloadFromYoutubeInternal(url, musicInfo, audioFormat)
        }
    }

    fun downloadFromYoutube(
        scope: CoroutineScope,
        url: String,
        musicInfo: MusicInfo,
        audioFormat: MusicInfo.AudioFormat,
    ) {
        downloadingJobs[url] = scope.launch {
            downloadFromYoutubeInternal(url, musicInfo, audioFormat)
        }
    }

    @Throws(DiskAccessException::class)
    private suspend fun downloadFromYoutubeInternal(
        url: String,
        musicInfo: MusicInfo,
        audioFormat: MusicInfo.AudioFormat,
    ) {

        val downloadingInfo = MusicDownloadingInfo(musicInfo, audioFormat)
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
            _downloadingFiles.value = allStates
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
            _downloadingFiles.value = allStates
        }
    }

    private inner class SingleSongDownloader(
        private val url: String,
        private val downloadingInfo: MusicDownloadingInfo,
    ) {

        suspend fun downloadMusic() {
            try {
                val musicFolder = prefsManager.getMusicDirectory()
                    ?: throw DiskAccessException("music folder not found")

                val progressFlow = fileManager.downloadFile(
                    url = downloadingInfo.selectedFormat.downloadUrl,
                    fileName = getSongFileName(),
                )

                progressFlow.collectLatest { downloadableFile ->
                    when (downloadableFile) {
                        is DownloadableFile.Progress -> updateProgress(url,
                            downloadableFile.progress)
                        is DownloadableFile.Complete -> {
                            copyFileToExternalStorage(downloadableFile.file, musicFolder)
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

        private fun copyFileToExternalStorage(internalFile: File, externalDirectory: Uri) {

            val outputDirectory = DocumentFile.fromTreeUri(context, externalDirectory)
                ?: throw IOException("Failed to open the directory $externalDirectory")

            val outputFile =
                outputDirectory.createFile("application/octet-stream", internalFile.name)
                    ?: throw IOException("Failed to create file")

            internalFile.copyTo(context, outputFile)
        }

        private fun getSongFileName(): String {
            return downloadingInfo.musicInfo.artist + ": " + downloadingInfo.musicInfo.title + "." + downloadingInfo.selectedFormat.extension
        }
    }
}
