package evgeniy.kurinnoy.musicdownloader.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val prefsManager: PrefsManager
): ViewModel() {

    private val _selectDirectory = MutableSharedFlow<String?>()
    val selectDirectory = _selectDirectory.asSharedFlow()

    init {
        viewModelScope.launch {
            if (prefsManager.getMusicDirectory() == null) {
                _selectDirectory.subscriptionCount.filter { it > 0 }.first()
                _selectDirectory.emit(null)
            }
        }
    }

    fun setMusicDirectory(uri: Uri) {
        viewModelScope.launch {
            prefsManager.setMusicDirectory(uri)
        }
    }

    fun changeMusicDirectory() {
        viewModelScope.launch {
            _selectDirectory.emit(prefsManager.getMusicDirectory()?.toString())
        }
    }
}