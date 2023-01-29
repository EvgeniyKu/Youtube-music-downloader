package evgeniy.kurinnoy.musicdownloader.data

import android.content.Context
import android.net.Uri
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import evgeniy.kurinnoy.musicdownloader.domain.exceptions.DiskAccessException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(FILE_NAME) }
    )

    suspend fun requireMusicDirectory(): Uri {
        return getMusicDirectory()
            ?: throw DiskAccessException("music folder not found")
    }

    suspend fun getMusicDirectory(): Uri? {
        val uriString = dataStore.data.first()[musicDirectoryKey]
            ?: return null
        return Uri.parse(uriString)
    }

    suspend fun setMusicDirectory(directory: Uri) {
        dataStore.edit {
            it[musicDirectoryKey] = directory.toString()
        }
    }

    companion object {
        private const val FILE_NAME = "music_downloader_prefs"

        private val musicDirectoryKey = stringPreferencesKey("music_directory_path_key")
    }
}