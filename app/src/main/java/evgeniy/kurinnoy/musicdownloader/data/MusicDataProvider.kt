package evgeniy.kurinnoy.musicdownloader.data

import android.net.Uri
import com.github.kiulian.downloader.YoutubeDownloader
import evgeniy.kurinnoy.musicdownloader.data.mappers.MusicInfoMapper
import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo
import evgeniy.kurinnoy.musicdownloader.utils.extension.getVideoInfo
import javax.inject.Inject

class MusicDataProvider @Inject constructor(
    private val downloader: YoutubeDownloader,
    private val mapper: MusicInfoMapper
) {

    suspend fun loadVideoInfo(url: String): MusicInfo {
        val id = extractVideoIdFromUrl(url)
        val videoInfo = downloader.getVideoInfo(id)

        return mapper.mapFrom(videoInfo).also {
            if (it.formats.isEmpty()) {
                throw IllegalArgumentException("not found audio for song $url")
            }
        }
    }

    private fun extractVideoIdFromUrl(url: String): String {
        val uri = Uri.parse(url)
        return when {
            url.contains("youtube.com") ->
                uri.getQueryParameter("v")
                    ?: throw IllegalArgumentException("can't extract video id from url $url")

            url.contains("youtu.be/") ->
                uri.lastPathSegment
                    ?: throw IllegalArgumentException("can't extract video id from url $url")

            else -> throw IllegalArgumentException("unsupported url $url")
        }
    }
}