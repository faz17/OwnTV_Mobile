package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.ChannelVersion
import tv.own.owntv.core.dispatcharr.DispatcharrApi
import tv.own.owntv.mobile.ui.screens.settings.dispatcharrFailureLabel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTag

class MobileDispatcharrSources(
    val load: suspend () -> DispatcharrApi.ChannelSources,
    val onPick: suspend (Int) -> Unit,
)

/**
 * "Sources": the live channel playing and its other versions — the same channel in another quality,
 * category or playlist, best picture first — from core's ChannelVersions, as on the television. The
 * one playing is ticked; a tap on another switches to it.
 */
@Composable
fun SourcesSheet(
    load: suspend () -> List<ChannelVersion>,
    playingId: Long,
    onPick: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    dispatcharr: MobileDispatcharrSources? = null,
) {
    val versions by produceState<List<ChannelVersion>?>(null) { value = load() }
    val remote by produceState<Result<DispatcharrApi.ChannelSources>?>(null, dispatcharr) {
        value = dispatcharr?.let { runCatching { it.load() } }
    }
    var switching by remember { mutableStateOf<Int?>(null) }
    var switchError by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.player_tool_sources)
    val separator = stringResource(R.string.content_epg_bits_separator)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)
        val list = versions ?: return@MobileBottomSheet
        val remoteStreams = remote?.getOrNull()
        val remoteIsLoading = dispatcharr != null && remote == null
        val remoteHasFailed = dispatcharr != null && remote?.isFailure == true
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            if (dispatcharr != null) {
                item(key = "dispatcharr_header") {
                    StageMenuItem(
                        stringResource(R.string.dispatcharr_mobile_title),
                        onClick = {},
                        icon = MobileIcons.LiveTv,
                        value = when {
                            remoteIsLoading -> stringResource(R.string.dispatcharr_mobile_loading)
                            remoteHasFailed -> stringResource(R.string.dispatcharr_mobile_load_failed)
                            else -> null
                        },
                        enabled = false,
                    )
                }
                if (remoteHasFailed) item(key = "dispatcharr_diagnostic") {
                    StageMenuItem(
                        dispatcharrFailureLabel(remote?.exceptionOrNull()),
                        onClick = {},
                        enabled = false,
                    )
                }
                if (remoteStreams != null) {
                    items(remoteStreams.streams, key = { "dispatcharr_${it.id}" }) { source ->
                        val active = source.id == remoteStreams.activeStreamId
                        StageMenuItem(
                            source.name,
                            onClick = {
                                if (!active && switching == null) {
                                    switching = source.id
                                    switchError = null
                                    scope.launch {
                                        val result = runCatching { dispatcharr.onPick(source.id) }
                                        switching = null
                                        if (result.isSuccess) onDismiss()
                                        else switchError = result.exceptionOrNull()
                                    }
                                }
                            },
                            icon = MobileIcons.LiveTv,
                            inlineTag = source.providerAccountName?.takeIf { it.isNotBlank() }?.let { accountName ->
                                { StageTag(accountName, modifier = Modifier.widthIn(max = 115.dp)) }
                            },
                            value = when {
                                switching == source.id -> stringResource(R.string.dispatcharr_mobile_switching)
                                active -> stringResource(R.string.dispatcharr_mobile_active)
                                else -> null
                            },
                            enabled = switching == null,
                            checked = active,
                        )
                    }
                }
                if (switchError != null) item(key = "dispatcharr_failed") {
                    StageMenuItem(dispatcharrFailureLabel(switchError), onClick = {}, enabled = false)
                }
                item(key = "playlist_versions_header") {
                    StageMenuItem(stringResource(R.string.dispatcharr_mobile_playlist_versions), onClick = {}, enabled = false)
                }
            }
            items(list, key = { it.channel.id }) { v ->
                val playing = v.channel.id == playingId
                StageMenuItem(
                    listOfNotNull(v.name, v.categoryName, v.playlistName).filter { it.isNotBlank() }.distinct().joinToString(separator),
                    onClick = { onDismiss(); if (!playing) onPick(v.channel) },
                    icon = MobileIcons.LiveTv,
                    value = v.tags.joinToString(" ").ifEmpty { null },
                    checked = playing,
                )
            }
            // Only the channel itself: say so, rather than leave a list of one that looks like a fault.
            if (list.size <= 1) item(key = "none") {
                StageMenuItem(stringResource(R.string.player_sources_none), onClick = onDismiss, enabled = false)
            }
        }
    }
}
