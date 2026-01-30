package com.kieronquinn.app.pcs.ui.screens.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.model.Release
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository.PhenotypeState
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.ui.screens.baseurl.dialog.BaseUrlDialog
import com.kieronquinn.app.pcs.ui.screens.loading.LoadingScreen
import com.kieronquinn.app.pcs.ui.screens.settings.SettingsViewModel.State
import com.kieronquinn.app.pcs.ui.screens.settings.SettingsViewModel.SyncState
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.kieronquinn.app.pcs.utils.extensions.openUrl
import com.kieronquinn.app.pcs.utils.extensions.switchPreference
import com.kieronquinn.app.pcs.utils.extensions.textResource
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import org.koin.androidx.compose.koinViewModel
import uk.co.bocajsolutions.cardshape.Shape

private data class Interactions (
    val onBuildLabelClicked: () -> Unit,
    val onSyncClicked: () -> Unit,
    val onRefreshClicked: () -> Unit,
    val onFaqClicked: () -> Unit,
    val onExperimentsClicked: () -> Unit,
    val onDebugChanged: (Boolean) -> Unit,
    val onAutoSyncChanged: (Boolean) -> Unit,
    val onFooterChipClicked: (FooterChip) -> Unit
) {
    companion object {
        val PREVIEW = Interactions (
            onBuildLabelClicked = {},
            onSyncClicked = {},
            onRefreshClicked = {},
            onFaqClicked = {},
            onDebugChanged = {},
            onExperimentsClicked = {},
            onAutoSyncChanged = { _ -> },
            onFooterChipClicked = {}
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen() {
    val viewModel = koinViewModel<SettingsViewModel>()
    var waitingForPermissionResponse by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val notificationPermissionState = rememberPermissionState(
        android.Manifest.permission.POST_NOTIFICATIONS
    )
    val interactions = Interactions (
        onBuildLabelClicked = viewModel::onBuildLabelClicked,
        onSyncClicked = viewModel::onSyncClicked,
        onRefreshClicked = viewModel::onRefreshClicked,
        onFaqClicked = viewModel::onFaqClicked,
        onExperimentsClicked = viewModel::onExperimentsClicked,
        onDebugChanged = {
            viewModel.onDebugChanged(it)
            Toast.makeText(context, R.string.screen_settings_debug_toast, Toast.LENGTH_LONG).show()
        },
        onAutoSyncChanged = { enabled ->
            val permissionGranted = notificationPermissionState.status.isGranted
            val shouldShowRationale = notificationPermissionState.status.shouldShowRationale
            // If the user rejected permissions, enable anyway and we won't notify them of updates
            if (!enabled || permissionGranted || shouldShowRationale) {
                viewModel.onAutoSyncChanged(enabled)
            } else {
                waitingForPermissionResponse = true
                notificationPermissionState.launchPermissionRequest()
            }
        },
        onFooterChipClicked = {
            when (it) {
                is FooterChip.NavigationLink -> {
                    viewModel.onDestinationSelected(it.destination)
                }
                is FooterChip.WebLink -> {
                    context.openUrl(it.url)
                }
            }
        }
    )
    when (state) {
        State.Loading -> LoadingContent()
        is State.Loaded -> LoadedContent(
            state as State.Loaded,
            interactions
        )
    }
    LaunchedEffect(
        waitingForPermissionResponse,
        notificationPermissionState.status.isGranted
    ) {
        if(waitingForPermissionResponse && notificationPermissionState.status.isGranted) {
            viewModel.onAutoSyncChanged(true)
            waitingForPermissionResponse = false
        }
    }
}

@Composable
private fun LoadingContent() {
    LoadingScreen()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalPermissionsApi::class)
@Composable
private fun LoadedContent(
    state: State.Loaded,
    interactions: Interactions
) = ProvidePreferenceLocals {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current
    var showEnterUrlDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
            .padding(horizontal = 8.dp)
    ) {
        val headerItemCount = if (state.updateState != null) 2 else 1
        val syncShape = Shape(headerItemCount, 0)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = syncShape)
                .clip(syncShape),
            key = "sync",
            icon = {
                if (state.syncState == SyncState.LOADING || state.syncState == SyncState.SYNCING) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    val icon = when (state.syncState) {
                        SyncState.REQUIRED -> R.drawable.ic_sync_available
                        SyncState.NOT_REQUIRED -> R.drawable.ic_sync
                        SyncState.ERROR -> R.drawable.ic_sync_error
                    }
                    Icon(painter = painterResource(icon), contentDescription = null)
                }
            },
            title = {
                val title = when (state.syncState) {
                    SyncState.NOT_REQUIRED -> R.string.screen_settings_sync_not_required_title
                    SyncState.REQUIRED -> R.string.screen_settings_sync_required_title
                    SyncState.LOADING -> R.string.screen_settings_sync_loading_title
                    SyncState.SYNCING -> R.string.screen_settings_sync_syncing_title
                    SyncState.ERROR -> R.string.screen_settings_sync_error_title
                }
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                val title = when (state.syncState) {
                    SyncState.NOT_REQUIRED -> R.string.screen_settings_sync_not_required_content
                    SyncState.REQUIRED -> R.string.screen_settings_sync_required_content
                    SyncState.LOADING -> R.string.screen_settings_sync_loading_content
                    SyncState.SYNCING -> R.string.screen_settings_sync_syncing_content
                    SyncState.ERROR -> R.string.screen_settings_sync_error_content
                }
                Text(text = stringResource(title))
            },
            enabled = state.syncState != SyncState.LOADING && state.syncState != SyncState.SYNCING,
            onClick = {
                when (state.syncState) {
                    SyncState.REQUIRED -> interactions.onSyncClicked()
                    SyncState.ERROR -> interactions.onRefreshClicked()
                    else -> {
                        // No-op
                    }
                }
            }
        )

        if (state.updateState != null) {
            item {
                Spacer(Modifier.height(2.dp))
            }

            val updateShape = Shape(headerItemCount, 1)
            preference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = updateShape)
                    .clip(updateShape),
                key = "update",
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_update),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.screen_settings_update_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                summary = {
                    val message = stringResource(
                        R.string.screen_settings_update_content,
                        BuildConfig.TAG_NAME,
                        state.updateState.tag
                    )
                    Text(text = message)
                },
                onClick = {
                    context.openUrl(state.updateState.gitHubUrl)
                }
            )
        }

        preferenceCategory(
            key = "category_settings",
            title = {
                Text(stringResource(R.string.screen_settings_category_settings))
            }
        )

        val settingsItemCount = 3
        val buildLabelShape = Shape(settingsItemCount, 0)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = buildLabelShape)
                .clip(buildLabelShape),
            key = "build_label",
            icon = {
                Icon(painter = painterResource(R.drawable.ic_build_label), contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_settings_build_label_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = stringResource(R.string.screen_settings_build_label_content))
            },
            onClick = interactions.onBuildLabelClicked
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val repositoryShape = Shape(settingsItemCount, 1)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = repositoryShape)
                .clip(repositoryShape),
            key = "repository",
            icon = {
                Icon(painter = painterResource(R.drawable.ic_repository), contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_settings_change_repository_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = stringResource(R.string.screen_settings_change_repository_content))
            },
            onClick = {
                showEnterUrlDialog = true
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val autoSyncShape = Shape(settingsItemCount, 2)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = autoSyncShape)
                .clip(autoSyncShape),
            value = state.autoSync,
            key = "auto_sync",
            icon = {
                Icon(painter = painterResource(R.drawable.ic_auto_sync), contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_settings_auto_sync_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = textResource(R.string.screen_settings_auto_sync_content))
            },
            onValueChange = interactions.onAutoSyncChanged
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        preferenceCategory(
            key = "category_advanced",
            title = {
                Text(stringResource(R.string.screen_settings_category_advanced))
            }
        )

        val advancedCategoryItemCount = 2
        val experimentsShape = Shape(advancedCategoryItemCount, 0)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = experimentsShape)
                .clip(experimentsShape),
            key = "experiments",
            icon = {
                Icon(painter = painterResource(R.drawable.ic_experiments), contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_settings_experiments_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = textResource(R.string.screen_settings_experiments_content))
            },
            onClick = interactions.onExperimentsClicked
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val debugShape = Shape(advancedCategoryItemCount, 1)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = debugShape)
                .clip(debugShape),
            value = state.propertiesState.debug,
            key = "debug",
            icon = {
                Icon(painter = painterResource(R.drawable.ic_debug), contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_settings_debug_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = textResource(R.string.screen_settings_debug_content))
            },
            onValueChange = interactions.onDebugChanged
        )

        preferenceCategory(
            key = "category_help",
            title = {
                Text(stringResource(R.string.screen_settings_category_help))
            }
        )

        val faqShape = Shape(1, 0)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = faqShape)
                .clip(faqShape),
            key = "faq",
            icon = {
                Icon(painter = painterResource(R.drawable.ic_help), contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_settings_faq_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = stringResource(R.string.screen_settings_faq_content))
            },
            onClick = interactions.onFaqClicked
        )

        preferenceCategory(
            key = "category_footer",
            title = {
                // No title
            }
        )

        item {
            Footer(interactions)
        }

        item {
            Spacer(Modifier.navigationBarsPadding()
                .padding(bottom = 16.dp))
        }
    }

    if (showEnterUrlDialog) {
        BaseUrlDialog(state.phenotypeState.repository) {
            showEnterUrlDialog = false
        }
    }
}

private sealed class FooterChip(
    @StringRes open val label: Int,
    @DrawableRes open val icon: Int
) {
    companion object {
        private const val BASE_URL = "https://kieronquinn.co.uk/redirect/pcs"

        val entries = listOf(
            Contributors,
            Donate,
            GitHub,
            Crowdin,
            Bluesky,
            XDA,
            Libraries
        )
    }

    data object Contributors: NavigationLink(
        R.string.screen_settings_footer_contributors,
        R.drawable.ic_contributors,
        destination = Destination.Contributors
    )

    data object Donate: WebLink(
        R.string.screen_settings_footer_donate,
        R.drawable.ic_donate,
        url = "$BASE_URL/donate"
    )

    data object GitHub: WebLink(
        R.string.screen_settings_footer_github,
        R.drawable.ic_github,
        url = "$BASE_URL/github"
    )

    data object Crowdin: WebLink(
        R.string.screen_settings_footer_crowdin,
        R.drawable.ic_crowdin,
        url = "$BASE_URL/crowdin"
    )

    data object Bluesky: WebLink(
        R.string.screen_settings_footer_bluesky,
        R.drawable.ic_bluesky,
        url = "$BASE_URL/bluesky"
    )

    data object XDA: WebLink(
        R.string.screen_settings_footer_xda,
        R.drawable.ic_xda,
        url = "$BASE_URL/xda"
    )

    data object Libraries: NavigationLink(
        R.string.screen_settings_footer_libraries,
        R.drawable.ic_libraries,
        destination = Destination.Libraries
    )

    open class WebLink(
        @StringRes override val label: Int,
        @DrawableRes override val icon: Int,
        open val url: String
    ): FooterChip(label, icon)

    open class NavigationLink(
        @StringRes override val label: Int,
        @DrawableRes override val icon: Int,
        open val destination: Destination
    ): FooterChip(label, icon)
}

@Composable
private fun Footer(interactions: Interactions) {
    val shape = Shape(1, 0)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(
                    R.string.screen_settings_footer_version,
                    BuildConfig.VERSION_NAME
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.screen_settings_footer_created_by
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FooterChip.entries.forEach {
                    InputChip(
                        onClick = {
                            interactions.onFooterChipClicked(it)
                        },
                        label = { Text(textResource(it.label)) },
                        selected = true,
                        avatar = {
                            Icon(
                                painterResource(it.icon),
                                contentDescription = null,
                                Modifier.size(InputChipDefaults.AvatarSize)
                                    .padding(2.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Preview(name = "Settings Content Light")
@Composable
private fun ContentPreviewLight() {
    PcsTheme {
        val state = State.Loaded(
            syncState = SyncState.LOADING,
            phenotypeState = PhenotypeState.Loaded(
                labels = null,
                repository = null,
                versions = emptyMap()
            ),
            propertiesState = PropertiesRepository.State(),
            updateState = Release("2.0", "", "", "", "", ""),
            autoSync = true
        )
        LoadedContent(state, Interactions.PREVIEW)
    }
}

@Preview(name = "Settings Content Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewDark() {
    ContentPreviewLight()
}