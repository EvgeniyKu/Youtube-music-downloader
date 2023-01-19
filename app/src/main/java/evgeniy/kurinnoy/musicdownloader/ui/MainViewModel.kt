package evgeniy.kurinnoy.musicdownloader.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import evgeniy.kurinnoy.musicdownloader.data.PrefsManager
import evgeniy.kurinnoy.musicdownloader.domain.MusicDownloadManager
import evgeniy.kurinnoy.musicdownloader.domain.exceptions.DiskAccessException
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefsManager: PrefsManager,
    @ApplicationContext private val context: Context
): ViewModel() {

    private val _selectDirectory = MutableSharedFlow<Uri?>()
    val selectDirectory = _selectDirectory.asSharedFlow()

    init {
        viewModelScope.launch {
            checkFolderAccess()
        }
    }

    fun setMusicDirectory(uri: Uri) {
        viewModelScope.launch {
            prefsManager.setMusicDirectory(uri)
        }
    }

    fun changeMusicDirectory() {
        viewModelScope.launch {
            _selectDirectory.emit(prefsManager.getMusicDirectory())
        }
    }

    private suspend fun checkFolderAccess() {
        val uri = prefsManager.getMusicDirectory()
        if (uri == null || DocumentFile.fromTreeUri(context, uri)?.canWrite() != true) {
            _selectDirectory.subscriptionCount.filter { it > 0 }.first()
            _selectDirectory.emit(uri)
        }
    }
}