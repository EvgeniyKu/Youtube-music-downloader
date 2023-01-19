package evgeniy.kurinnoy.musicdownloader.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import dagger.hilt.android.AndroidEntryPoint
import evgeniy.kurinnoy.musicdownloader.R
import evgeniy.kurinnoy.musicdownloader.data.models.MusicInfo
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingInfo
import evgeniy.kurinnoy.musicdownloader.domain.models.MusicDownloadingState
import evgeniy.kurinnoy.musicdownloader.ui.service.DownloadService
import evgeniy.kurinnoy.musicdownloader.utils.rememberService
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainViewModel>()

    private val openDocumentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setMusicDirectory(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleIntent(intent = intent)
        setContent {
           ProvideWindowInsets {
               Content()
           }
        }
    }

    override fun onResume() {
        super.onResume()
        startService(Intent(this@MainActivity, DownloadService::class.java))
    }

    @Composable
    private fun Content() {
        MaterialTheme(
            colors = darkColors(primary = Color.Cyan)
        ) {
            val service by rememberService<DownloadService>()

            LaunchedEffect(true) {
                viewModel.selectDirectory.onEach {
                    openDocumentTree.launch(it)
                }.launchIn(this)
            }

            LaunchedEffect(service) {
                service?.error?.onEach {
                    Toast.makeText(this@MainActivity, "Error: ${it.message}", Toast.LENGTH_LONG)
                        .show()
                }?.launchIn(this)
            }

            Box {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(id = R.drawable.main_background),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
                AnimatedVisibility(
                    visible = service != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    service?.let { service ->
                        val files = service.downloadManager.downloadingFiles
                            .collectAsState(emptyList())
                        MainScreen(
                            downloadingFiles = files,
                            onDownloadClick = { service.downloadMusic(it) },
                            onRestart = { service.tryAgain(it) },
                            onCancel = { service.cancel(it.id) },
                            onChangeFolder = { viewModel.changeMusicDirectory() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.extras?.getString(Intent.EXTRA_TEXT)
        url?.let {
            val serviceIntent = DownloadService.loadMusicIntent(this, it)
            startService(serviceIntent)
        }
    }
}

@Composable
private fun MainScreen(
    downloadingFiles: State<List<MusicDownloadingState>>,
    onDownloadClick: (String) -> Unit,
    onRestart: (info: MusicDownloadingState.Failure) -> Unit,
    onCancel: (state: MusicDownloadingState) -> Unit,
    onChangeFolder: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp)
                .clickable(onClick = onChangeFolder)
                .align(Alignment.TopEnd),
            painter = painterResource(id = R.drawable.ic_folder),
            contentDescription = "change folder",
            tint = MaterialTheme.colors.onSurface
        )

        Image(
            modifier = Modifier
                .padding(top = 36.dp)
                .size(232.dp, 63.dp)
                .padding(horizontal = 24.dp)
                .align(BiasAlignment(verticalBias = -0.65f, horizontalBias = 0f)),
            painter = painterResource(id = R.drawable.app_title),
            contentDescription = "application name"
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = BiasAlignment.Horizontal(0f),
        ) {
            var currentUrl by remember {
                mutableStateOf("")
            }
            UrlInput(
                url = currentUrl,
                onTextChange = { currentUrl = it }
            )
            val focusManager = LocalFocusManager.current
            DownloadButton(
                modifier = Modifier.padding(top = 16.dp)
            ) {
                onDownloadClick(currentUrl)
                currentUrl = ""
                focusManager.clearFocus()
            }
        }

        DownloadingList(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxHeight(0.3f)
                .align(Alignment.BottomCenter),
            list = downloadingFiles,
            onClick = {
                if (it is MusicDownloadingState.Failure) {
                    onRestart(it)
                }
            },
            onCancel = onCancel
        )
    }
}

@Composable
private fun DownloadingList(
    modifier: Modifier = Modifier,
    list: State<List<MusicDownloadingState>>,
    onClick: (MusicDownloadingState) -> Unit,
    onCancel: (MusicDownloadingState) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        list.value.forEach {
            SongRow(
                state = it,
                onClick = { onClick(it) },
                onCancel = { onCancel(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

}

@Composable
private fun SongRow(
    state: MusicDownloadingState,
    onClick: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            is MusicDownloadingState.Completed -> Icon(
                modifier = Modifier.size(40.dp),
                painter = painterResource(id = R.drawable.ic_success),
                contentDescription = null,
                tint = MaterialTheme.colors.primary
            )
            is MusicDownloadingState.Failure -> Icon(
                modifier = Modifier.size(40.dp),
                painter = painterResource(id = R.drawable.ic_failure),
                contentDescription = null,
                tint = MaterialTheme.colors.error
            )
            is MusicDownloadingState.InProgress -> {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${state.progress.toInt()}%",
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body2
                    )
                    CircularProgressIndicator()
                }
            }
            is MusicDownloadingState.Pending -> {
                CircularProgressIndicator()
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        val title = when (state) {
            is MusicDownloadingState.Pending -> state.url
            is MusicDownloadingState.InfoState ->
                "${state.info.musicInfo.artist}: ${state.info.musicInfo.title}"
        }
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface
        )

        Image(
            modifier = Modifier.clickable(onClick = onCancel),
            painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
            contentDescription = "cancel"
        )
    }

}

@Composable
private fun UrlInput(
    modifier: Modifier = Modifier,
    url: String,
    onTextChange: (String) -> Unit,
) {
    val shape = remember {
        RoundedCornerShape(16.dp)
    }
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.2f), shape = shape),
        value = url,
        onValueChange = onTextChange,
        placeholder = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.input_url),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1,
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = MaterialTheme.colors.onSurface,
            unfocusedBorderColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high)
        ),
        shape = shape
    )
}

@Composable
private fun DownloadButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = remember {
        RoundedCornerShape(16.dp)
    }
    Text(
        modifier = modifier
            .border(1.dp, Color.Red, shape = shape)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        text = stringResource(id = R.string.download),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.h6,
        color = MaterialTheme.colors.onSurface
    )
}

@Composable
@Preview
private fun MainPreview() {
    val audioFormat =
        MusicInfo.AudioFormat(0, 0, MusicInfo.Quality.HIGH, "mp3", 1000, "dsdfs")
    val info = MusicDownloadingInfo(
        musicInfo = MusicInfo("Song", "Artist", listOf(audioFormat)),
        selectedFormat = audioFormat
    )
    val state = MusicDownloadingState.InProgress(
        info.copy(),
        90f,
        "youtube.com/Jhgjbjj35"
    )

    val stateSuccess = MusicDownloadingState.Completed(
        info = info.copy(),
        url = "youtube.com/Jhgjbjj35"
    )

    val stateError = MusicDownloadingState.Failure(
        info = info.copy(),
        url = "youtube.com/Jhgjbjj35",
        throwable = Throwable()
    )

    val states = remember {
        mutableStateOf(
            listOf(
                MusicDownloadingState.Pending("some_url.com"),
                state,
                stateSuccess,
                stateError
            )
        )
    }
    MaterialTheme(
        colors = darkColors(primary = Color.Cyan)
    ) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(id = R.drawable.main_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds
        )
        MainScreen(
            downloadingFiles = states,
            onDownloadClick = { },
            onRestart = { },
            onCancel = { },
            onChangeFolder = { }
        )
    }
}