package evgeniy.kurinnoy.musicdownloader.domain.models

import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo

data class MusicDownloadingInfo(
    val musicInfo: MusicInfo,
    val selectedFormat: MusicInfo.AudioFormat
)