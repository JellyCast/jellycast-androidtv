package nl.jellycast.mobile.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.CheckedSheetRow
import nl.jellycast.mobile.core.designsystem.component.SheetRow
import nl.jellycast.mobile.core.designsystem.component.SheetTitle
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.navigation.AddToListRequest
import nl.jellycast.mobile.navigation.LocalAppNavigation
import org.koin.androidx.compose.koinViewModel

/**
 * The "Add to list" sheet while `AppNavigation.addToListRequest` is set, and the message when a
 * list change could not be saved.
 */
@Composable
fun AddToListHost(viewModel: AddToListViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    val request = navigation.addToListRequest
    LaunchedEffect(request) { if (request != null) viewModel.onOpen() }
    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            navigation.addToListRequest = null
            viewModel.onClosed()
        }
    }
    if (request != null) {
        AddToListSheet(
            request = request,
            state = state,
            onAdd = { viewModel.onAdd(it, request.itemIds) },
            onCreate = { viewModel.onCreate(it, request.itemIds) },
            onDismiss = {
                navigation.addToListRequest = null
                viewModel.onClosed()
            },
        )
    }
    state.changeError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::onChangeErrorShown,
            title = { Text(stringResource(R.string.lists_change_failed)) },
            text = if (message.isEmpty()) null else ({ Text(message) }),
            confirmButton = {
                TextButton(onClick = viewModel::onChangeErrorShown) {
                    Text(stringResource(R.string.lists_ok))
                }
            },
            containerColor = JellyCastColors.Surface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    request: AddToListRequest,
    state: AddToListUiState,
    onAdd: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isNaming by rememberSaveable { mutableStateOf(false) }
    val targets = remember(state.lists, request) { addTargets(state.lists, request.itemIds) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = JellyCastColors.Surface,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(
                stringResource(R.string.lists_add_title),
                subtitle = stringResource(R.string.lists_add_subtitle, request.title),
            )
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.lists.isEmpty() && state.load != ListsLoad.Loaded) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), Alignment.Center) {
                        CircularProgressIndicator(color = JellyCastColors.TextMuted)
                    }
                }
                targets.forEach { target ->
                    CheckedSheetRow(
                        title = target.list.name,
                        selected = target.position != null,
                        onClick = { if (target.position == null) onAdd(target.list.id) },
                        detail = detail(target, request.itemIds.size),
                        modifier = Modifier.alpha(
                            if (state.addingTo ==
                                target.list.id
                            ) {
                                0.5f
                            } else {
                                1f
                            },
                        ),
                    )
                }
                SheetRow(
                    title = stringResource(R.string.lists_new_ellipsis),
                    onClick = { isNaming = true },
                    icon = R.drawable.ic_lists_add,
                    iconTint = JellyCastColors.Accent,
                )
            }
        }
    }
    if (isNaming) {
        ListNameDialog(
            title = stringResource(R.string.lists_new),
            confirmTitle = stringResource(R.string.lists_create),
            onConfirm = onCreate,
            onDismiss = { isNaming = false },
        )
    }
}

@Composable
private fun detail(target: AddTarget, itemCount: Int): String {
    val count = target.list.entries.size
    val titles = pluralStringResource(R.plurals.lists_titles, count, count)
    val position = target.position ?: return titles
    val already =
        if (itemCount == 1) {
            stringResource(R.string.lists_already_in_as, position)
        } else {
            stringResource(R.string.lists_already_in)
        }
    return "$titles · $already"
}
