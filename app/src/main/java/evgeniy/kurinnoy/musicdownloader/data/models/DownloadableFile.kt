package evgeniy.kurinnoy.musicdownloader.data.models

import androidx.annotation.FloatRange
import java.io.File

sealed class DownloadableFile {

    data class Progress(
        @FloatRange(from = 0.0, to = 100.0) val progress: Float
    ) : DownloadableFile()

    data class Complete(val file: File) : DownloadableFile()
}