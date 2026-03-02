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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import com.kieronquinn.app.pcs.ui.components.InfoCard
import com.kieronquinn.app.pcs.ui.screens.experiments.ExperimentsViewModel.State
import com.kieronquinn.app.pcs.ui.screens.loading.LoadingScreen
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.kieronquinn.app.pcs.utils.extensions.listPreference
import com.kieronquinn.app.pcs.utils.extensions.switchPreference
import com.kieronquinn.app.pcs.utils.extensions.textResource
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preferenceCategory
import org.koin.androidx.compose.koinViewModel
import uk.co.bocajsolutions.cardshape.Shape

private data class Interactions(
    val onPhoneSharpieEnabledChanged: (Boolean) -> Unit,
    val onPhoneDobbyEnabledChanged: (Boolean) -> Unit,
    val onPhoneDobbyRegionChanged: (DobbyRegion) -> Unit,
    val onPhoneAtlasEnabledChanged: (Boolean) -> Unit,
    val onPhoneBeeslyEnabledChanged: (Boolean) -> Unit,
    val onPhoneBeeslyRegionChanged: (BeeslyRegion) -> Unit,
    val onPhoneNautilusEnabledChanged: (Boolean) -> Unit,
    val onPhoneSonicEnabledChanged: (Boolean) -> Unit,
    val onPhoneXatuEnabledChanged: (Boolean) -> Unit,
    val onPhoneCallerTagsEnabledChanged: (Boolean) -> Unit,
    val onPhoneFermatEnabledChanged: (Boolean) -> Unit,
    val onPhoneExpressoEnabledChanged: (Boolean) -> Unit,
    val onPhonePatrickChanged: (PatrickPhase) -> Unit,
    val onPhoneCallRecordingEnabledChanged: (Boolean) -> Unit,
    val onPsiAppsChanged: (Boolean) -> Unit,
    val onPsiForceAccountPresenceChanged: (Boolean) -> Unit,
    val onPsiForceAccountTypeChanged: (Boolean) -> Unit,
    val onPsiForceAdminAllowanceChanged: (Boolean) -> Unit
) {
    companion object {
        val PREVIEW = Interactions(
            onPhoneSharpieEnabledChanged = {},
            onPhoneDobbyEnabledChanged = {},
            onPhoneDobbyRegionChanged = {},
            onPhoneAtlasEnabledChanged = {},
            onPhoneBeeslyEnabledChanged = {},
            onPhoneBeeslyRegionChanged = {},
            onPhoneNautilusEnabledChanged = {},
            onPhoneSonicEnabledChanged = {},
            onPhoneXatuEnabledChanged = {},
            onPhoneCallerTagsEnabledChanged = {},
            onPhoneFermatEnabledChanged = {},
            onPhoneExpressoEnabledChanged = {},
            onPhonePatrickChanged = {},
            onPhoneCallRecordingEnabledChanged = {},
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
        onPhoneSharpieEnabledChanged = viewModel::onPhoneSharpieEnabledChanged,
        onPhoneDobbyEnabledChanged = viewModel::onPhoneDobbyEnabledChanged,
        onPhoneDobbyRegionChanged = viewModel::onPhoneDobbyRegionChanged,
        onPhoneAtlasEnabledChanged = viewModel::onPhoneAtlasEnabledChanged,
        onPhoneBeeslyEnabledChanged = viewModel::onPhoneBeeslyEnabledChanged,
        onPhoneBeeslyRegionChanged = viewModel::onPhoneBeeslyRegionChanged,
        onPhoneNautilusEnabledChanged = viewModel::onPhoneNautilusEnabledChanged,
        onPhoneSonicEnabledChanged = viewModel::onPhoneSonicEnabledChanged,
        onPhoneXatuEnabledChanged = viewModel::onPhoneXatuEnabledChanged,
        onPhoneCallerTagsEnabledChanged = viewModel::onPhoneCallerTagsEnabledChanged,
        onPhoneFermatEnabledChanged = viewModel::onPhoneFermatEnabledChanged,
        onPhoneExpressoEnabledChanged = viewModel::onPhoneExpressoEnabledChanged,
        onPhonePatrickChanged = viewModel::onPhonePatrickChanged,
        onPhoneCallRecordingEnabledChanged = viewModel::onPhoneCallRecordingEnabledChanged,
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
    val resources = LocalResources.current
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

        val phoneFlagsCount = 11
        val sharpieShape = Shape(phoneFlagsCount, 0)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = sharpieShape)
                .clip(sharpieShape),
            value = state.phoneSettings.sharpieEnabled,
            key = "phone_flags_sharpie",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_sharpie),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_sharpie)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneSharpieEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val dobbyShape = Shape(phoneFlagsCount, 1)
        val dobbyDisabled = state.phoneSettings.dobbyUrl.isNullOrBlank()
                || state.phoneSettings.dobbyDuplexFiles.isNullOrBlank()
        val dobbyEnabled = state.phoneSettings.dobbyEnabled && !dobbyDisabled
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = dobbyShape)
                .clip(dobbyShape),
            value = dobbyEnabled,
            enabled = { !dobbyDisabled },
            key = "phone_flags_dobby",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_dobby),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                if (!dobbyDisabled) {
                    val title = stringResource(R.string.screen_experiments_phone_feature_dobby)
                    Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
                } else {
                    Text(text = stringResource(R.string.screen_experiments_phone_feature_disabled))
                }
            },
            onValueChange = {
                interactions.onPhoneDobbyEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }
        
        if (dobbyEnabled) {
            val dobbyRegionShape = Shape(phoneFlagsCount, 1)
            listPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = dobbyRegionShape)
                    .clip(dobbyRegionShape),
                value = state.phoneSettings.dobbyRegion,
                values = DobbyRegion.entries,
                key = "phone_flags_dobby_region",
                title = { _ ->
                    Text(
                        text = stringResource(R.string.screen_experiments_phone_feature_dobby_region),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                valueToText = {
                    AnnotatedString(resources.getString(it.label))
                },
                summary = {
                    Text(text = stringResource(it.label))
                },
                onValueChange = {
                    interactions.onPhoneDobbyRegionChanged(it)
                }
            )

            item {
                Spacer(Modifier.height(2.dp))
            }
        }

        val atlasShape = Shape(phoneFlagsCount, 2)
        val atlasDisabled = state.phoneSettings.atlasModels.isNullOrBlank()
        val atlasEnabled = state.phoneSettings.atlasEnabled && !atlasDisabled
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = atlasShape)
                .clip(atlasShape),
            value = atlasEnabled,
            enabled = { !atlasDisabled },
            key = "phone_flags_atlas",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_atlas),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                if (!atlasDisabled) {
                    val title = stringResource(R.string.screen_experiments_phone_feature_atlas)
                    Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
                } else {
                    Text(text = stringResource(R.string.screen_experiments_phone_feature_disabled))
                }
            },
            onValueChange = {
                interactions.onPhoneAtlasEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val beeslyShape = Shape(phoneFlagsCount, 3)
        val beeslyDisabled = state.phoneSettings.beesly.isNullOrBlank()
        val beeslyEnabled = state.phoneSettings.beeslyEnabled && !beeslyDisabled
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = beeslyShape)
                .clip(beeslyShape),
            value = beeslyEnabled,
            enabled = { !beeslyDisabled },
            key = "phone_flags_beesly",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_beesly),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                if (!beeslyDisabled) {
                    val title = stringResource(R.string.screen_experiments_phone_feature_beesly)
                    Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
                } else {
                    Text(text = stringResource(R.string.screen_experiments_phone_feature_disabled))
                }
            },
            onValueChange = {
                interactions.onPhoneBeeslyEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        if (beeslyEnabled) {
            val beeslyRegionShape = Shape(phoneFlagsCount, 3)
            listPreference(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(color = surface, shape = beeslyRegionShape)
                    .clip(beeslyRegionShape),
                value = state.phoneSettings.beeslyRegion,
                values = BeeslyRegion.entries,
                key = "phone_flags_beesly_region",
                title = { _ ->
                    Text(
                        text = stringResource(R.string.screen_experiments_phone_feature_beesly_region),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                valueToText = {
                    AnnotatedString(resources.getString(it.label))
                },
                summary = {
                    Text(text = stringResource(it.label))
                },
                onValueChange = {
                    interactions.onPhoneBeeslyRegionChanged(it)
                }
            )

            item {
                Spacer(Modifier.height(2.dp))
            }
        }

        val nautilusShape = Shape(phoneFlagsCount, 4)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = nautilusShape)
                .clip(nautilusShape),
            value = state.phoneSettings.nautilusEnabled,
            key = "phone_flags_nautilus",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_nautilus),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_nautilus)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneNautilusEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val sonicShape = Shape(phoneFlagsCount, 5)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = sonicShape)
                .clip(sonicShape),
            value = state.phoneSettings.sonicEnabled,
            key = "phone_flags_sonic",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_sonic),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_sonic)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneSonicEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val xatuShape = Shape(phoneFlagsCount, 5)
        val xatuDisabled = state.phoneSettings.xatuModels.isNullOrBlank()
        val xatuEnabled = state.phoneSettings.xatuEnabled && !xatuDisabled
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = xatuShape)
                .clip(xatuShape),
            value = xatuEnabled,
            enabled = { !xatuDisabled },
            key = "phone_flags_xatu",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_xatu),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                if (!xatuDisabled) {
                    val title = stringResource(R.string.screen_experiments_phone_feature_xatu)
                    Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
                } else {
                    Text(text = stringResource(R.string.screen_experiments_phone_feature_disabled))
                }
            },
            onValueChange = {
                interactions.onPhoneXatuEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val callerTagsShape = Shape(phoneFlagsCount, 6)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = callerTagsShape)
                .clip(callerTagsShape),
            value = state.phoneSettings.callerTagsEnabled,
            key = "phone_flags_caller_tags",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_caller_tags),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_caller_tags)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneCallerTagsEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val fermatShape = Shape(phoneFlagsCount, 7)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = fermatShape)
                .clip(fermatShape),
            value = state.phoneSettings.fermatEnabled,
            key = "phone_flags_fermat",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_fermat),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_fermat)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneFermatEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val expressiveShape = Shape(phoneFlagsCount, 8)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = expressiveShape)
                .clip(expressiveShape),
            value = state.phoneSettings.expressoEnabled,
            key = "phone_flags_expresso",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_expresso),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_expresso)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneExpressoEnabledChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val patrickShape = Shape(phoneFlagsCount, 9)
        listPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = patrickShape)
                .clip(patrickShape),
            value = state.phoneSettings.patrickPhase,
            values = PatrickPhase.entries,
            key = "phone_flags_patrick",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_patrick),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            valueToText = {
                AnnotatedString(resources.getString(it.label))
            },
            summary = {
                Text(text = stringResource(it.label))
            },
            onValueChange = {
                interactions.onPhonePatrickChanged(it)
            }
        )

        item {
            Spacer(Modifier.height(2.dp))
        }

        val callRecordingShape = Shape(phoneFlagsCount, 10)
        switchPreference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = callRecordingShape)
                .clip(callRecordingShape),
            value = state.phoneSettings.callRecordingEnabled,
            key = "phone_flags_call_recording",
            title = { _ ->
                Text(
                    text = stringResource(R.string.screen_experiments_phone_feature_call_recording),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = { _ ->
                val title = stringResource(R.string.screen_experiments_phone_feature_call_recording)
                Text(text = stringResource(R.string.screen_experiments_phone_subtitle, title))
            },
            onValueChange = {
                interactions.onPhoneCallRecordingEnabledChanged(it)
            }
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
            phoneSettings = PhoneSettings(
                sharpieEnabled = false,
                dobbyEnabled = false,
                dobbyUrl = null,
                dobbyDuplexFiles = null,
                dobbyRegion = DobbyRegion.US,
                atlasEnabled = false,
                atlasModels = null,
                beeslyEnabled = false,
                beesly = null,
                beeslyRegion = BeeslyRegion.US,
                nautilusEnabled = false,
                sonicEnabled = false,
                xatuEnabled = false,
                xatuModels = null,
                callerTagsEnabled = false,
                fermatEnabled = false,
                expressoEnabled = false,
                patrickPhase = PatrickPhase.PHASE_TWO,
                callRecordingEnabled = false
            ),
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