package tv.own.owntv.mobile.ui.screens.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageLabel
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
    val completed = recordings.filter { it.isCompleted() }.sortedByDescending { it.startMs }
    val scheduled = recordings.filter { it.isScheduled(now) || it.isActive(now) }.sortedBy { it.startMs }

    Column(modifier.fillMaxSize()) {
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
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item(key = "completed_header") {
                    StageLabel(
                        stringResource(R.string.recording_group_completed),
                        Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 5.dp),
                    )
                }
                if (completed.isEmpty()) item(key = "completed_empty") {
                    Text(stringResource(R.string.dispatcharr_remote_empty_completed),
                        style = stageText(12f, 500),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                items(completed, key = { it.id }) { record ->
                    RemoteRecordingRow(
                        record = record,
                        onClick = { if (record.hasFile) play(record) else menuFor = record },
                        onLongClick = { menuFor = record },
                    )
                }
                item(key = "scheduled_header") {
                    StageLabel(
                        stringResource(R.string.recording_group_scheduled),
                        Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 5.dp),
                    )
                }
                if (scheduled.isEmpty()) item(key = "scheduled_empty") {
                    Text(stringResource(R.string.dispatcharr_remote_empty_scheduled),
                        style = stageText(12f, 500),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                items(scheduled, key = { it.id }) { record ->
                    RemoteRecordingRow(
                        record = record,
                        onClick = { menuFor = record },
                        onLongClick = { menuFor = record },
                    )
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

/** Small Dispatcharr programme poster with a clear fallback when EPG artwork is absent. */
@Composable
private fun RemoteRecordingRow(
    record: DispatcharrApi.ServerRecording,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val active = record.isActive(System.currentTimeMillis())
    val palette = LocalStagePalette.current
    StageRow(onClick = onClick, onLongClick = onLongClick, current = active, minHeight = 76.dp) {
        Box(
            Modifier.padding(start = 8.dp, end = 9.dp)
                .width(48.dp).height(68.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(palette.cardSolid),
            contentAlignment = Alignment.Center,
        ) {
            if (record.posterUrl != null) {
                AsyncImage(
                    model = record.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                StageIcon(MobileIcons.LiveTv, 22.dp, palette.dim)
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(record.title, style = stageText(14f, 700), color = palette.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(record.startMs)),
                style = stageText(12f, 500), color = if (active) palette.danger else palette.muted)
        }
        if (active) Text(
            stringResource(R.string.recording_group_now),
            style = stageText(12f, 600),
            color = palette.danger,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
