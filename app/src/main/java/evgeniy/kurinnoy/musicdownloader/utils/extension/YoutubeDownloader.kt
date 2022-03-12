package evgeniy.kurinnoy.musicdownloader.utils.extension

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.model.videos.VideoInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun YoutubeDownloader.getVideoInfo(videoId: String): VideoInfo {
    return suspendCancellableCoroutine<VideoInfo> { continuation ->
        val request = RequestVideoInfo(videoId)
            .callback(object: YoutubeCallback<VideoInfo> {
                override fun onFinished(data: VideoInfo) {
                    continuation.resume(data)
                }

                override fun onError(throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            })

        val response = getVideoInfo(request)
        continuation.invokeOnCancellation {
            response.cancel()
        }
    }
}