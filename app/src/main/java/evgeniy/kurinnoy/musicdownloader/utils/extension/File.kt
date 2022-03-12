package evgeniy.kurinnoy.musicdownloader.utils.extension

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

@Throws(IOException::class, IllegalStateException::class)
fun File.copyTo(
    context: Context,
    target: DocumentFile,
): Boolean {
    if (!this.exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }

    if (this.isDirectory) {
        throw IllegalStateException("Copying a directory not supported")
    }

    if (!target.isFile) {
        throw IOException("The destination file is not a file")
    }

    val targetOutputStream = context.contentResolver.openOutputStream(target.uri)
        ?: throw IOException("Failed to open output stream of target DocumentFile")

    this.inputStream().use { sourceStream ->
        targetOutputStream.use { targetStream ->
            sourceStream.copyTo(targetStream)
        }
    }

    return true
}