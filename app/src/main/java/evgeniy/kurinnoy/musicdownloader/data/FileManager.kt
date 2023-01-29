package evgeniy.kurinnoy.musicdownloader.data

import android.content.Context
import android.net.Uri
import android.util.Range
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import evgeniy.kurinnoy.musicdownloader.data.models.DownloadableFile
import evgeniy.kurinnoy.musicdownloader.utils.extension.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.IOException
import java.net.URL
import javax.inject.Inject

class FileManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {

    private val rootDirectory: File = context.cacheDir

    private val directory: File by lazy {
        File("${rootDirectory}${File.separator}$TEMP_DIRECTORY").also {
            val file = File(it.absolutePath)
            file.mkdirs()
        }
    }

    fun downloadFile(url: String, fileName: String): Flow<DownloadableFile> {
        return flow {
            val file = downloadFile(url, fileName) { progress ->
                emit(DownloadableFile.Progress(progress))
            }
            emit(DownloadableFile.Complete(file))
        }.flowOn(Dispatchers.IO)
    }

    suspend fun downloadFile(
        url: String,
        fileName: String,
        progressCollector: suspend (Float) -> Unit,
    ): File {

        val file = createTempFile(fileName)
        file.parentFile?.mkdirs()

        // Downloading process wasn't finished by some reason
        if (file.exists() && !file.canRead()) {
            file.delete()
        }

        file.setReadable(false)

        val byteBuffer = ByteArray(BUFFER_SIZE)

        val connection = URL(url).openConnection()
        connection.getInputStream().use { input ->
            file.outputStream().use { output ->
                while (true) {
                    try {
                        //check is the job active
                        yield()

                        val length = input.read(byteBuffer)
                        if (length > 0) {
                            output.write(byteBuffer, 0, length)
                            val progress = file.length() * 100F / connection.contentLengthLong
                            progressCollector(percentageRange.clamp(progress))
                        } else {
                            break
                        }
                    } catch (ex: Exception) {
                        file.delete()
                        throw ex
                    }
                }
            }
        }
        file.setReadable(true)
        progressCollector(100F)
        return file

    }

    fun copyFileToExternalStorage(internalFile: File, externalDirectory: Uri) {
        val outputDirectory = openExternalDirectory(externalDirectory)

        val outputFile =
            outputDirectory.createFile("application/octet-stream", internalFile.name)
                ?: throw IOException("Failed to create file")

        internalFile.copyTo(context, outputFile)
    }

    fun isExistInExternalDirectory(externalDirectory: Uri, fileName: String): Boolean {
        return openExternalDirectory(externalDirectory).findFile(fileName) != null
    }

    private fun openExternalDirectory(uri: Uri): DocumentFile {
        return DocumentFile.fromTreeUri(context, uri)
            ?.takeIf { it.exists() && it.canWrite() }
            ?: throw IOException("Failed to open the directory $uri")
    }

    private fun createTempFile(fileName: String): File {
        return File(directory, fileName).apply {
            createNewFile()
        }
    }

    companion object {
        private val percentageRange = Range(0F, 100F)
        private const val BUFFER_SIZE = 8 * 1024

        private const val TEMP_DIRECTORY = "temp_music"
    }
}