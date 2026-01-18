package com.kieronquinn.app.pcs.ui.screens.buildlabel

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.model.BuildLabel
import com.kieronquinn.app.pcs.model.proto.Labels
import com.kieronquinn.app.pcs.repositories.NavigationRepository.MenuItem
import com.kieronquinn.app.pcs.ui.components.InfoCard
import com.kieronquinn.app.pcs.ui.screens.buildlabel.BuildLabelViewModel.Event
import com.kieronquinn.app.pcs.ui.screens.buildlabel.BuildLabelViewModel.State
import com.kieronquinn.app.pcs.ui.screens.loading.LoadingScreen
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.kieronquinn.app.pcs.utils.extensions.textResource
import kotlinx.coroutines.flow.Flow
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.radioButtonPreference
import org.koin.androidx.compose.koinViewModel
import uk.co.bocajsolutions.cardshape.Shape

private data class Interactions (
    val onSetLabel: (BuildLabel) -> Unit,
    val onResetClicked: () -> Unit,
    val onSetDismiss: () -> Unit,
    val onResetDismiss: () -> Unit,
) {
    companion object {
        val PREVIEW = Interactions (
            onSetLabel = {},
            onResetClicked = {},
            onSetDismiss = {},
            onResetDismiss = {}
        )
    }
}

@Composable
fun BuildLabelScreen(onMenuItemSelected: Flow<MenuItem>) {
    val viewModel = koinViewModel<BuildLabelViewModel>()
    var showSetDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val interactions = Interactions (
        onSetLabel = viewModel::setLabel,
        onSetDismiss = {
            showSetDialog = false
        },
        onResetClicked = {
            showResetDialog = false
            viewModel.resetLabel()
        },
        onResetDismiss = {
            showResetDialog = false
        }
    )
    when (state) {
        State.Loading -> LoadingContent()
        is State.Loaded -> LoadedContent(
            state as State.Loaded,
            interactions,
            showSetDialog,
            showResetDialog
        )
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect {
            when (it) {
                Event.LABEL_SET -> {
                    // Only show the set dialog if the label has changed
                    if ((state as? State.Loaded)?.label != null) {
                        showSetDialog = true
                    }
                }
                Event.LABEL_RESET -> {
                    Toast.makeText(
                        context,
                        R.string.screen_build_label_toast_reset,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        onMenuItemSelected.collect {
            when (it.text) {
                R.string.screen_build_label_reset -> showResetDialog = true
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    LoadingScreen()
}

@Composable
private fun LoadedContent(
    state: State.Loaded,
    interactions: Interactions,
    showSetDialog: Boolean = false,
    showResetDialog: Boolean = false
) = ProvidePreferenceLocals {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    var showConfirmDialog by remember { mutableStateOf<BuildLabel?>(null) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
            .padding(horizontal = 8.dp)
    ) {
        item {
            val content = if (state.label == null) {
                textResource(R.string.screen_build_label_info_set)
            } else {
                textResource(R.string.screen_build_label_info_change)
            }
            InfoCard(
                modifier = Modifier.padding(horizontal = 8.dp),
                icon = R.drawable.ic_info,
                content = content
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        BuildLabel.entries.forEachIndexed { index, label ->
            val shape = Shape(BuildLabel.entries.size, index)
            val selected = label.device == state.label?.deviceTier &&
                    label.variant == state.label.variant
            radioButtonPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = shape)
                    .clip(shape),
                key = label.name,
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                R.string.screen_build_label_variant,
                                label.variant.name
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(
                                R.string.screen_build_label_device_tier,
                                label.device.format()
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                summary = {
                    val deviceList = label.devices.joinToString(", ").ifEmpty {
                        stringResource(R.string.screen_build_label_device_none)
                    }
                    Text(
                        text = pluralStringResource(
                            R.plurals.screen_build_label_devices,
                            label.devices.size,
                            deviceList
                        )
                    )
                },
                onClick = {
                    showConfirmDialog = label
                },
                selected = selected,
            )
            item {
                Spacer(Modifier.height(2.dp))
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
        }
        item {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    val showConfirmDialogFor = showConfirmDialog
    if (showConfirmDialogFor != null) {
        val content = stringResource(
            R.string.screen_build_label_dialog_set_content,
            showConfirmDialogFor.variant.name,
            showConfirmDialogFor.device.format()
        )
        BuildLabelDialog(
            title = stringResource(R.string.screen_build_label_dialog_set_title),
            content = content,
            onConfirm = {
                showConfirmDialog = null
                interactions.onSetLabel(showConfirmDialogFor)
            },
            onDismiss = {
                showConfirmDialog = null
            }
        )
    }
    if (showSetDialog) {
        BuildLabelDialog(
            title = stringResource(R.string.screen_build_label_dialog_changed_title),
            content = stringResource(R.string.screen_build_label_dialog_changed_content),
            showCancel = false,
            onConfirm = interactions.onSetDismiss,
        )
    }
    if (showResetDialog) {
        BuildLabelDialog(
            title = stringResource(R.string.screen_build_label_dialog_reset_title),
            content = stringResource(R.string.screen_build_label_dialog_reset_content),
            onConfirm = interactions.onResetClicked,
            onDismiss = interactions.onResetDismiss
        )
    }
}

@Composable
private fun BuildLabelDialog(
    title: String,
    content: String,
    showCancel: Boolean = true,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                textAlign = TextAlign.Start
            )
        },
        text = {
            Column {
                Text(text = content)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            if (showCancel) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun BlobConstraints.DeviceTier.format(): String {
    val resource = when (this) {
        BlobConstraints.DeviceTier.ULTRA_LOW -> R.string.device_tier_ultra_low
        BlobConstraints.DeviceTier.LOW -> R.string.device_tier_low
        BlobConstraints.DeviceTier.MID -> R.string.device_tier_mid
        BlobConstraints.DeviceTier.HIGH -> R.string.device_tier_high
        BlobConstraints.DeviceTier.ULTRA -> R.string.device_tier_ultra
        else -> return name
    }
    return stringResource(resource)
}

@Preview(name = "Build Label Content Light")
@Composable
private fun ContentPreviewLight() {
    PcsTheme {
        LoadedContent(State.Loaded(Labels.newBuilder().build()), Interactions.PREVIEW)
    }
}

@Preview(name = "Build Label Content Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewDark() {
    PcsTheme {
        LoadedContent(State.Loaded(Labels.newBuilder().build()), Interactions.PREVIEW)
    }
}