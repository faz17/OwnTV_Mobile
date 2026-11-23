package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.dispatcharr.DispatcharrApi
import tv.own.owntv.mobile.R

/**
 * Safe on-screen diagnostics: show only the operation and HTTP status, never API response
 * bodies or arbitrary network exception messages that might contain credential-bearing URLs.
 */
@Composable
fun dispatcharrFailureLabel(error: Throwable?): String {
    val failure = error as? DispatcharrApi.ApiFailure
    if (failure != null) {
        val stage = stringResource(
            when (failure.stage) {
                DispatcharrApi.ApiStage.AUTH -> R.string.dispatcharr_diag_auth
                DispatcharrApi.ApiStage.CHANNEL -> R.string.dispatcharr_diag_channel
                DispatcharrApi.ApiStage.STREAMS -> R.string.dispatcharr_diag_streams
                DispatcharrApi.ApiStage.STATUS -> R.string.dispatcharr_diag_status
                DispatcharrApi.ApiStage.SWITCH -> R.string.dispatcharr_diag_switch
            },
        )
        val code = failure.statusCode
        return if (code != null) stringResource(R.string.dispatcharr_diag_http, stage, code)
            else stringResource(R.string.dispatcharr_diag_network, stage)
    }
    return stringResource(R.string.dispatcharr_mobile_load_failed)
}
