package evgeniy.kurinnoy.musicdownloader.utils.extension

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.model.videos.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun YoutubeDownloader.getVideoInfo(videoId: String): VideoInfo? {
    return withContext(Dispatchers.IO) {
        val request = RequestVideoInfo(videoId)
        getVideoInfo(request).data(10, TimeUnit.SECONDS)
    }
}