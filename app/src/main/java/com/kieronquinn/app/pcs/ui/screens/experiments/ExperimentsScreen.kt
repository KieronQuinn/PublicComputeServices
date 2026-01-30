package com.kieronquinn.app.pcs.ui.screens.experiments

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.ui.components.InfoCard
import com.kieronquinn.app.pcs.ui.screens.experiments.ExperimentsViewModel.State
import com.kieronquinn.app.pcs.ui.screens.loading.LoadingScreen
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.kieronquinn.app.pcs.utils.extensions.switchPreference
import com.kieronquinn.app.pcs.utils.extensions.textResource
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preferenceCategory
import org.koin.androidx.compose.koinViewModel
import uk.co.bocajsolutions.cardshape.Shape

private data class Interactions(
    val onPhoneFlagsChanged: (Boolean) -> Unit,
    val onPsiAppsChanged: (Boolean) -> Unit,
    val onPsiForceAccountPresenceChanged: (Boolean) -> Unit,
    val onPsiForceAccountTypeChanged: (Boolean) -> Unit,
    val onPsiForceAdminAllowanceChanged: (Boolean) -> Unit
) {
    companion object {
        val PREVIEW = Interactions(
            onPhoneFlagsChanged = {},
            onPsiAppsChanged = {},
            onPsiForceAccountPresenceChanged = {},
            onPsiForceAccountTypeChanged = {},
            onPsiForceAdminAllowanceChanged = {}
        )
    }
}

@Composable
fun ExperimentsScreen() = ProvidePreferenceLocals {
    val viewModel = koinViewModel<ExperimentsViewModel>()
    val state by viewModel.state.collectAsState()
    val interactions = Interactions(
        onPhoneFlagsChanged = viewModel::onPhoneFlagsChanged,
        onPsiAppsChanged = viewModel::onPsiAppsChanged,
        onPsiForceAccountPresenceChanged = viewModel::onPsiForceAccountPresenceChanged,
        onPsiForceAccountTypeChanged = viewModel::onPsiForceAccountTypeChanged,
        onPsiForceAdminAllowanceChanged = viewModel::onPsiForceAdminAllowanceChanged
    )
    when (state) {
        State.Loading -> LoadingContent()
        is State.Loaded -> LoadedContent(
            state as State.Loaded,
            interactions
        )
    }
}

@Composable
private fun LoadingContent() {
    LoadingScreen()
}

@Composable
private fun LoadedContent(state: State.Loaded, interactions: Interactions) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
            .padding(horizontal = 8.dp)
    ) {
        item {
            InfoCard(
                modifier = Modifier.padding(horizontal = 8.dp),
                icon = R.drawable.ic_info,
                content = textResource(R.string.screen_experiments_info)
            )
        }

        preferenceCategory(
            key = "category_phone",
            title = {
                Text(stringResource(R.string.screen_experiments_category_google_phone))
            }
        )

        val phoneShape = Shape(1, 0)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = phoneShape)
                .clip(phoneShape),
            value = state.propertiesState.phoneFlags,
            key = "phone_flags",
            title = {
                Text(
                    text = stringResource(R.string.screen_experiments_phone_flags_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = textResource(R.string.screen_experiments_phone_flags_content))
            },
            onValueChange = interactions.onPhoneFlagsChanged
        )

        if(state.magicCueAvailable) {
            preferenceCategory(
                key = "category_psi",
                title = {
                    Text(stringResource(R.string.screen_experiments_category_psi))
                }
            )

            val magicCueItemCount = 4
            val appsShape = Shape(magicCueItemCount, 0)
            switchPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = appsShape)
                    .clip(appsShape),
                value = state.propertiesState.psiApps,
                key = "psi_apps",
                title = {
                    Text(
                        text = stringResource(R.string.screen_experiments_psi_enable_apps_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                summary = {
                    Text(text = textResource(R.string.screen_experiments_psi_enable_apps_content))
                },
                onValueChange = interactions.onPsiAppsChanged
            )

            item {
                Spacer(Modifier.height(2.dp))
            }

            val accountPresenceShape = Shape(magicCueItemCount, 1)
            switchPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = accountPresenceShape)
                    .clip(accountPresenceShape),
                value = state.propertiesState.psiForceAccountPresence,
                key = "psi_account_presence",
                title = {
                    Text(
                        text = stringResource(R.string.screen_experiments_psi_force_account_presence_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                summary = {
                    Text(text = textResource(R.string.screen_experiments_psi_force_account_presence_content))
                },
                onValueChange = interactions.onPsiForceAccountPresenceChanged
            )

            item {
                Spacer(Modifier.height(2.dp))
            }

            val accountTypeShape = Shape(magicCueItemCount, 2)
            switchPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = accountTypeShape)
                    .clip(accountTypeShape),
                value = state.propertiesState.psiForceAccountType,
                key = "psi_account_type",
                title = {
                    Text(
                        text = stringResource(R.string.screen_experiments_psi_force_account_type_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                summary = {
                    Text(text = textResource(R.string.screen_experiments_psi_force_account_type_content))
                },
                onValueChange = interactions.onPsiForceAccountTypeChanged
            )

            item {
                Spacer(Modifier.height(2.dp))
            }

            val adminAllowanceShape = Shape(magicCueItemCount, 3)
            switchPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = adminAllowanceShape)
                    .clip(adminAllowanceShape),
                value = state.propertiesState.psiForceAdminAllowance,
                key = "psi_admin_allowance",
                title = {
                    Text(
                        text = stringResource(R.string.screen_experiments_psi_force_admin_allowance_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                summary = {
                    Text(text = textResource(R.string.screen_experiments_psi_force_admin_allowance_content))
                },
                onValueChange = interactions.onPsiForceAdminAllowanceChanged
            )
        }

        item {
            Spacer(Modifier.navigationBarsPadding()
                .padding(bottom = 16.dp))
        }
    }
}

@Preview(name = "Settings Content Light")
@Composable
private fun ContentPreviewLight() {
    PcsTheme {
        val state = State.Loaded(
            magicCueAvailable = true,
            propertiesState = PropertiesRepository.State()
        )
        LoadedContent(state, Interactions.PREVIEW)
    }
}

@Preview(name = "Settings Content Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewDark() {
    ContentPreviewLight()
}