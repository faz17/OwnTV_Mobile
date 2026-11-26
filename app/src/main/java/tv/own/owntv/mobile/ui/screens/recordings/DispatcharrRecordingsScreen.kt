package tv.own.owntv.mobile.ui.screens.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import tv.own.owntv.core.dispatcharr.DispatcharrApi
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.library.VodTuner
import tv.own.owntv.mobile.ui.screens.settings.DispatcharrConnectionStore
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Local library and remote DVR have separate ownership and storage. Never
 * silently fall back to recording on the phone when Dispatcharr is selected.
 */
@Composable
fun DispatcharrRecordingsDestination(onPlayerOpened: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val backend by settings.recordingBackend.collectAsStateWithLifecycle(SettingsRepository.RecordingBackend.AUTO)
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { DispatcharrConnectionStore.get(context) }
    val connection by store.connection.collectAsStateWithLifecycle()
    val remote = when (backend) {
        SettingsRepository.RecordingBackend.LOCAL -> false
        SettingsRepository.RecordingBackend.DISPATCHARR -> true
        SettingsRepository.RecordingBackend.AUTO -> connection != null
    }
    if (remote) RemoteRecordings(onPlayerOpened, modifier)
    else RecordingsScreen(onPlayerOpened, modifier)
}

@Composable
private fun RemoteRecordings(onPlayerOpened: () -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { DispatcharrConnectionStore.get(context) }
    val connection by store.connection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val vodTuner: VodTuner = koinInject()
    val client = remember(connection) {
        connection?.let { saved ->
            DispatcharrApi(
                OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
                saved.origin, saved.apiKey,
            )
        }
    }
    var reload by remember { mutableIntStateOf(0) }
    var recordings by remember { mutableStateOf<List<DispatcharrApi.ServerRecording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var completedTab by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DispatcharrApi.ServerRecording?>(null) }

    LaunchedEffect(client, reload) {
        loading = client != null
        failed = false
        recordings = emptyList()
        if (client != null) {
            runCatching { client.serverRecordings() }
                .onSuccess { recordings = it }
                .onFailure { failed = true }
        }
        loading = false
    }

    fun play(record: DispatcharrApi.ServerRecording) {
        val api = client ?: return
        scope.launch {
            if (vodTuner.playServerRecording(
                api.serverRecordingPlaybackUrl(record.id),
                record.title,
                api.serverRecordingHeaders(),
            )) onPlayerOpened()
        }
    }

    val now = System.currentTimeMillis()
    val visible = if (completedTab) {
        recordings.filter { it.isCompleted() }.sortedByDescending { it.startMs }
    } else {
        recordings.filter { it.isScheduled(now) || it.isActive(now) }.sortedBy { it.startMs }
    }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StageButton(
                onClick = { completedTab = false },
                text = stringResource(R.string.recording_group_scheduled),
                icon = MobileIcons.Schedule, small = true,
            )
            StageButton(
                onClick = { completedTab = true },
                text = stringResource(R.string.recording_group_completed),
                icon = MobileIcons.PlayArrow, small = true,
            )
        }
        StageMenuItem(
            stringResource(R.string.dispatcharr_remote_refresh),
            onClick = { reload++ },
            icon = MobileIcons.Refresh,
        )
        when {
            client == null -> Text(
                stringResource(R.string.dispatcharr_remote_unconfigured),
                modifier = Modifier.padding(16.dp), style = stageText(13f, 500),
            )
            loading -> Text(
                stringResource(R.string.dispatcharr_mobile_loading),
                modifier = Modifier.padding(16.dp), style = stageText(13f, 500),
            )
            failed -> Text(
                stringResource(R.string.dispatcharr_remote_load_failed),
                modifier = Modifier.padding(16.dp), style = stageText(13f, 500),
            )
            visible.isEmpty() -> Text(
                stringResource(if (completedTab) R.string.dispatcharr_remote_empty_completed
                else R.string.dispatcharr_remote_empty_scheduled),
                modifier = Modifier.padding(16.dp), style = stageText(13f, 500),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { rec ->
                    val active = rec.isActive(System.currentTimeMillis())
                    StageRow(
                        onClick = {
                            if (completedTab && rec.hasFile) play(rec)
                            else menuFor = rec
                        },
                        onLongClick = { menuFor = rec },
                        current = active,
                        minHeight = 70.dp,
                    ) {
                        Column(Modifier.weight(1f).padding(10.dp)) {
                            Text(
                                rec.title, style = stageText(14f, 700),
                                color = LocalStagePalette.current.text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(rec.startMs)),
                                style = stageText(12f, 500),
                                color = if (active) LocalStagePalette.current.danger
                                    else LocalStagePalette.current.muted,
                            )
                        }
                        if (active) Text(
                            stringResource(R.string.recording_group_now),
                            style = stageText(12f, 600),
                            color = LocalStagePalette.current.danger,
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { rec ->
        MobileBottomSheet(onDismissRequest = { menuFor = null }, title = rec.title) {
            StageSheetHeader(
                title = rec.title,
                subtitle = DateFormat.getDateTimeInstance().format(Date(rec.startMs)),
            )
            if (rec.isCompleted()) {
                StageMenuItem(
                    stringResource(R.string.content_downloads_play),
                    onClick = { menuFor = null; play(rec) },
                    icon = MobileIcons.PlayArrow,
                )
            } else if (rec.isActive(System.currentTimeMillis()) ||
                rec.isScheduled(System.currentTimeMillis())) {
                StageMenuItem(
                    stringResource(if (rec.isActive(System.currentTimeMillis()))
                        R.string.recording_stop else R.string.common_cancel),
                    onClick = {
                        scope.launch {
                            runCatching {
                                if (rec.isActive(System.currentTimeMillis())) client?.stopServerRecording(rec.id)
                                else client?.cancelServerRecording(rec.id)
                            }.onSuccess { reload++ }.onFailure { failed = true }
                        }
                        menuFor = null
                    },
                    icon = MobileIcons.Record,
                )
            }
        }
    }
}
