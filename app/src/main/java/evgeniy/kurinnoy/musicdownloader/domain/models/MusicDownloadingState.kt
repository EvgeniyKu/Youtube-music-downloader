package evgeniy.kurinnoy.musicdownloader.domain.models

sealed class MusicDownloadingState(
    val id: String,
) {

    data class Pending(val url: String) : MusicDownloadingState(url)

    sealed class InfoState(
        url: String,
    ) : MusicDownloadingState(url) {
        abstract val info: MusicDownloadingInfo
    }

    data class InProgress(
        override val info: MusicDownloadingInfo,
        val progress: Float,
        val url: String,
    ) : InfoState(url)

    data class Completed(
        override val info: MusicDownloadingInfo,
        val url: String,
    ) : InfoState(url)

    data class Failure(
        override val info: MusicDownloadingInfo,
        val throwable: Throwable,
        val url: String,
    ) : InfoState(url)

    fun description(): String {
        return when(this) {
            is InfoState -> {
                val shortInfo = "${info.musicInfo.artist}: ${info.musicInfo.title}"
                when(this) {
                    is Completed -> "Successfully downloaded $shortInfo"
                    is Failure -> "Failed to download $shortInfo. Error: $throwable\n${throwable.stackTraceToString()}"
                    is InProgress -> "Progress $progress $shortInfo"
                }
            }
            is Pending -> "Pending: $url"
        }
    }
}
