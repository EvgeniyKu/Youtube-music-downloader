package evgeniy.kurinnoy.musicdownloader.data.models

data class MusicInfo(
    val title: String,
    val artist: String,
    val formats: List<AudioFormat>
) {

    val bestFormat: AudioFormat
        get() = formats.maxByOrNull { it.quality.ordinal }!!

    data class AudioFormat(
        val bitrate: Int,
        val sampleRate: Int,
        val quality: Quality,
        val extension: String,
        val durationMs: Long,
        val downloadUrl: String
    )

    enum class Quality {
        UNKNOWN, LOW, MEDIUM, HIGH
    }
}
