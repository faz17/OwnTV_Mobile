package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageChevron
import tv.own.owntv.mobile.ui.stage.StageLabel
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.drawInnerRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Settings › Layout › Navigation, the phone's version of the television's Navigation popup: the
 * bottom bar with or without names, whether it slides away while scrolling, which places it shows
 * (Dynamic follows the playlist; Static lists the places to pick, right here), and counts in the sheets.
 */
@Composable
fun NavigationSheet(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val labels = vm.settings.phoneNavLabels.pref(true)
    val hideOnScroll = vm.settings.phoneNavHideOnScroll.pref(true)
    val mode = vm.settings.navMenuMode.pref(SettingsRepository.NavMenuMode.STATIC)
    val hidden = vm.settings.navMenuHidden.pref(emptySet())
    val counts = vm.settings.sheetCounts.pref(true)
    val savedOrder = vm.settings.navMenuOrder.pref(MainSection.browseOrder)
    var editingOrder by remember { mutableStateOf<List<MainSection>?>(null) }
    val ordered = editingOrder ?: savedOrder
    LaunchedEffect(savedOrder) {
        if (editingOrder == savedOrder) editingOrder = null
    }
    val title = stringResource(R.string.settings_navigation)
    val dynamic = mode == SettingsRepository.NavMenuMode.DYNAMIC

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(
            title = title,
            subtitle = stringResource(R.string.common_nav_settings) + " · " + stringResource(R.string.settings_group_layout),
        )
        StageLabel(stringResource(R.string.settings_nav_bar), Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp))
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceButton(stringResource(R.string.settings_nav_labels), labels, Modifier.weight(1f)) { vm.edit { setPhoneNavLabels(true) } }
            ChoiceButton(stringResource(R.string.settings_nav_icons_only), !labels, Modifier.weight(1f)) { vm.edit { setPhoneNavLabels(false) } }
        }
        StageSettingRow(
            title = stringResource(R.string.settings_nav_hide_scrolling),
            icon = MobileIcons.OpenInFull,
            subtitle = stringResource(R.string.settings_nav_hide_scrolling_desc),
            role = Role.Switch,
            onClick = { vm.edit { setPhoneNavHideOnScroll(!hideOnScroll) } },
        ) { StageSwitch(hideOnScroll) }
        // Two choices, so a tap flips between them rather than opening a second sheet over this one.
        StageSettingRow(
            title = stringResource(R.string.settings_nav_menu_items),
            icon = MobileIcons.MoreHoriz,
            subtitle = stringResource(R.string.settings_nav_menu_items_desc),
            onClick = {
                vm.edit {
                    setNavMenuMode(if (dynamic) SettingsRepository.NavMenuMode.STATIC else SettingsRepository.NavMenuMode.DYNAMIC)
                }
            },
        ) { StageRowValue(stringResource(if (dynamic) R.string.settings_dynamic else R.string.settings_static), StageChevron.DOWN) }
        StageLabel(stringResource(R.string.dispatcharr_nav_reorder_title), Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
        // Both modes share the order. Dynamic hides unavailable sections automatically,
        // Static shows switches. Search and More remain pinned outside this list.
        Column(Modifier.fillMaxWidth()) {
            ordered.forEach { section ->
                val index = ordered.indexOf(section)
                val shown = section.name !in hidden
                val toggleable = !dynamic && section != MainSection.HOME
                val move: (Int) -> Unit = { direction ->
                    val current = (editingOrder ?: savedOrder).toMutableList()
                    val from = current.indexOf(section)
                    val to = (from + direction).coerceIn(0, current.lastIndex)
                    if (from >= 0 && to != from) {
                        current.add(to, current.removeAt(from))
                        editingOrder = current
                    }
                }
                androidx.compose.runtime.key(section) {
                    NavOrderRow(
                        title = stringResource(section.labelRes),
                        shown = shown,
                        toggleable = toggleable,
                        onToggle = {
                            vm.edit { setNavMenuHidden(if (shown) hidden + section.name else hidden - section.name) }
                        },
                        onMove = move,
                        onDrop = {
                            editingOrder?.let { update -> vm.edit { setNavMenuOrder(update) } }
                        },
                    )
                }
            }
        }
        StageSettingRow(
            title = stringResource(R.string.settings_sheet_counts),
            icon = MobileIcons.Tiles,
            subtitle = stringResource(R.string.settings_sheet_counts_desc),
            role = Role.Switch,
            onClick = { vm.edit { setSheetCounts(!counts) } },
        ) { StageSwitch(counts) }
    }
}

/** `.row2 span`: one of two side-by-side choices, 40 dp; the chosen one accent-tinted with its ring. */
@Composable
private fun ChoiceButton(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) accent.copy(alpha = 0.18f) else palette.controlFill)
            .then(if (on) Modifier.drawBehind { drawInnerRing(accent.copy(alpha = 0.6f), 1.2.dp.toPx(), 12.dp.toPx()) } else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = stageText(13f, 700), color = if (on) accent else palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Classic six-square drag grip. Long-press the grip (not the toggle) and
 * move up or down; the new order is persisted on finger release.
 */
@Composable
private fun NavOrderRow(
    title: String,
    shown: Boolean,
    toggleable: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onDrop: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val onMoveNow by rememberUpdatedState(onMove)
    val onDropNow by rememberUpdatedState(onDrop)
    val dragLabel = stringResource(R.string.dispatcharr_nav_drag_handle, title)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StageSettingRow(
            title = title,
            modifier = Modifier.weight(1f),
            role = if (toggleable) Role.Switch else null,
            onClick = if (toggleable) onToggle else null,
            minHeight = 48.dp,
        ) {
            if (toggleable) StageSwitch(shown)
        }
        Box(
            Modifier
                .size(48.dp)
                .semantics { contentDescription = dragLabel }
                .pointerInput(title) {
                    var travelled = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { travelled = 0f },
                        onDrag = { change, delta ->
                            change.consume()
                            travelled += delta.y
                            if (abs(travelled) >= threshold) {
                                onMoveNow(if (travelled > 0) 1 else -1)
                                travelled -= if (travelled > 0) threshold else -threshold
                            }
                        },
                        onDragEnd = { onDropNow() },
                        onDragCancel = { onDropNow() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val dot = 5.dp.toPx()
                val radius = CornerRadius(1.dp.toPx())
                for (row in 0..2) for (col in 0..1) {
                    drawRoundRect(
                        color = palette.muted,
                        topLeft = Offset(col * 11.dp.toPx(), row * 8.dp.toPx()),
                        size = Size(dot, dot),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}
