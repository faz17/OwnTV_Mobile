package tv.own.owntv.mobile.ui.screens.recordings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageSegmented

private const val RECORDINGS_VIEW_PREFERENCES = "owntv_recordings_view"
private const val RECORDINGS_GRID_PREFERENCE = "grid_view"

/** One local preference for the device, shared by the local and Dispatcharr libraries. */
@Composable
internal fun rememberRecordingGridPreference(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences(RECORDINGS_VIEW_PREFERENCES, Context.MODE_PRIVATE)
    }
    var grid by remember(prefs) { mutableStateOf(prefs.getBoolean(RECORDINGS_GRID_PREFERENCE, false)) }
    return grid to { next ->
        grid = next
        prefs.edit().putBoolean(RECORDINGS_GRID_PREFERENCE, next).apply()
    }
}

/** Same two-choice view control in both DVR backends. */
@Composable
internal fun RecordingsViewSwitch(grid: Boolean, onChange: (Boolean) -> Unit) {
    StageSegmented(
        options = listOf(
            stringResource(R.string.dispatcharr_recordings_view_list),
            stringResource(R.string.dispatcharr_recordings_view_grid),
        ),
        selected = if (grid) 1 else 0,
        onSelect = { onChange(it == 1) },
        icons = listOf<ImageVector>(MobileIcons.FormatListBulleted, MobileIcons.GridView),
        iconOnly = true,
    )
}
