package evgeniy.kurinnoy.musicdownloader.data.mappers

import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.quality.AudioQuality
import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo
import javax.inject.Inject

class AudioFormatMapper @Inject constructor() {

    fun mapFrom(format: AudioFormat): MusicInfo.AudioFormat {
        return MusicInfo.AudioFormat(
            bitrate = format.bitrate(),
            sampleRate = format.audioSampleRate(),
            quality = mapQuality(format.audioQuality()),
            extension = format.extension().value(),
            durationMs = format.duration(),
            downloadUrl = format.url()
        )
    }

    private fun mapQuality(quality: AudioQuality): MusicInfo.Quality {
        return when (quality) {
            AudioQuality.unknown,
            AudioQuality.noAudio -> MusicInfo.Quality.UNKNOWN
            AudioQuality.low -> MusicInfo.Quality.LOW
            AudioQuality.medium -> MusicInfo.Quality.MEDIUM
            AudioQuality.high -> MusicInfo.Quality.HIGH
        }
    }
}