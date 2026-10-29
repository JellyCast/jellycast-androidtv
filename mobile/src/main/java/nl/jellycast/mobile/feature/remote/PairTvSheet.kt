package nl.jellycast.mobile.feature.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.AccentButton
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.remote.PairingTarget
import org.koin.compose.viewmodel.koinViewModel

private const val DISABLED_ALPHA = 0.45f
private const val FAINT_ALPHA = 0.12f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairTvSheet(
    target: PairingTarget,
    onDismiss: () -> Unit,
    viewModel: PairTvViewModel = koinViewModel(key = "pair-${target.deviceId}"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(target.deviceId) {
        viewModel.onStart(target)
        onDispose { viewModel.onCancel() }
    }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.phase) {
        if (state.phase == PairingPhase.Paired) dismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellyCastColors.Surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.remote_pair_chromecast),
                    style = JellyCastText.Title2.copy(fontWeight = FontWeight.Bold),
                    color = JellyCastColors.Text,
                )
                Text(
                    stringResource(R.string.remote_pair_explanation),
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                )
            }
            DeviceCard(target)
            when (val phase = state.phase) {
                PairingPhase.Connecting, PairingPhase.Paired -> Waiting()

                PairingPhase.EnterCode, PairingPhase.Pairing ->
                    CodeEntry(
                        code = state.code,
                        canPair = state.canPair,
                        onCodeChange = viewModel::onCodeChange,
                        onPair = viewModel::onPair,
                    )

                is PairingPhase.Failed -> {
                    Text(
                        stringResource(phase.reason.message()),
                        style = JellyCastText.Subheadline,
                        color = JellyCastColors.Text,
                    )
                    AccentButton(
                        stringResource(R.string.remote_try_again),
                        onClick = { viewModel.onStart(target) },
                        icon = R.drawable.ic_remote_refresh,
                    )
                }
            }
        }
    }
}

private fun PairingFailure.message(): Int = when (this) {
    PairingFailure.NoAnswer -> R.string.remote_no_answer
    PairingFailure.WrongCode -> R.string.remote_wrong_code
    PairingFailure.Failed -> R.string.remote_pairing_failed
}

@Composable
private fun Waiting() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_remote_hourglass),
            null,
            Modifier.size(16.dp),
            JellyCastColors.TextMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.remote_code_soon),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
        )
    }
}

private val CardShape = RoundedCornerShape(18.dp)

@Composable
private fun DeviceCard(target: PairingTarget) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(JellyCastColors.Accent.copy(alpha = FAINT_ALPHA))
            .border(1.5.dp, JellyCastColors.Accent, CardShape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(JellyCastColors.Accent.copy(alpha = FAINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_tv),
                null,
                Modifier.size(18.dp),
                JellyCastColors.Accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                target.name,
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
            )
            Text(
                listOf(stringResource(R.string.remote_google_tv), target.host)
                    .joinToString(" · "),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
            )
        }
        Icon(
            painterResource(R.drawable.ic_remote_check_circle),
            null,
            Modifier.size(22.dp),
            JellyCastColors.Accent,
        )
    }
}

/** Six boxes over one hidden field, so the keyboard types straight into them. */
@Composable
private fun CodeEntry(
    code: String,
    canPair: Boolean,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.remote_type_code),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
        )
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onPair() }),
            decorationBox = { CodeBoxes(code) },
        )
        AccentButton(
            stringResource(R.string.remote_pair),
            onClick = onPair,
            modifier = Modifier.alpha(if (canPair) 1f else DISABLED_ALPHA),
            icon = R.drawable.ic_remote_link,
        )
    }
}

private val BoxShape = RoundedCornerShape(14.dp)

@Composable
private fun CodeBoxes(code: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PairTvUiState.CODE_LENGTH) { index ->
            val character = code.getOrNull(index)
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(BoxShape)
                    .background(JellyCastColors.Text.copy(alpha = 0.08f))
                    .border(
                        1.5.dp,
                        if (character != null) {
                            JellyCastColors.Accent
                        } else {
                            JellyCastColors.Text.copy(alpha = 0.1f)
                        },
                        BoxShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    character?.toString().orEmpty(),
                    style =
                        JellyCastText.Title2.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = JellyCastColors.Text,
                )
            }
        }
    }
}
