package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageTextRow
import tv.own.owntv.mobile.ui.stage.StageMenuItem

/** Explicit opt-in settings, using the same secure local-credential pattern as the TV fork. */
@Composable
fun SettingsDispatcharrPage() {
    val context = LocalContext.current
    val store = remember(context) { DispatcharrConnectionStore.get(context) }
    val saved by store.connection.collectAsStateWithLifecycle()
    var seeded by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (!seeded) {
            origin = saved?.origin.orEmpty()
            key = saved?.apiKey.orEmpty()
            seeded = true
        }
    }

    SettingsStagePage {
        item(key = "dispatcharr_help") { PageNote(stringResource(R.string.dispatcharr_mobile_description)) }
        item(key = "dispatcharr_origin") {
            StageTextRow(
                MobileIcons.Edit,
                stringResource(R.string.dispatcharr_mobile_url),
                origin,
                { origin = it; completed = false },
                placeholder = stringResource(R.string.dispatcharr_mobile_url_hint),
            )
        }
        item(key = "dispatcharr_key") {
            StageTextRow(
                MobileIcons.Lock,
                stringResource(R.string.dispatcharr_mobile_api_key),
                key,
                { key = it; completed = false },
                password = true,
            )
        }
        item(key = "dispatcharr_save") {
            StageMenuItem(
                stringResource(R.string.dispatcharr_mobile_save),
                onClick = {
                    failed = runCatching { store.save(origin, key) }.isFailure
                    completed = !failed
                },
                icon = MobileIcons.Edit,
                value = when {
                    failed -> stringResource(R.string.dispatcharr_mobile_invalid)
                    completed -> stringResource(R.string.dispatcharr_mobile_saved)
                    else -> null
                },
            )
        }
        item(key = "dispatcharr_remove") {
            StageMenuItem(
                stringResource(R.string.dispatcharr_mobile_remove),
                onClick = {
                    store.clear()
                    origin = ""
                    key = ""
                    completed = false
                    failed = false
                },
                icon = MobileIcons.Lock,
                value = if (saved != null) stringResource(R.string.dispatcharr_mobile_configured) else null,
            )
        }
    }
}
