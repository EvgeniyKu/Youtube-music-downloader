package evgeniy.kurinnoy.musicdownloader.data.mappers

import com.github.kiulian.downloader.model.videos.VideoInfo
import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo
import javax.inject.Inject

class MusicInfoMapper @Inject constructor(
    private val audioFormatMapper: AudioFormatMapper
) {

    fun mapFrom(videoInfo: VideoInfo): MusicInfo {
        return MusicInfo(
            title = videoInfo.details().title(),
            artist = videoInfo.details().author(),
            formats = videoInfo.audioFormats().map { format ->
                audioFormatMapper.mapFrom(format)
            }
        )
    }
}