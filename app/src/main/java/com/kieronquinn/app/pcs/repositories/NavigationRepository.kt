package com.kieronquinn.app.pcs.repositories

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavEntry
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination
import com.kieronquinn.app.pcs.repositories.NavigationRepository.MenuItem
import com.kieronquinn.app.pcs.repositories.NavigationRepository.NavigationEvent
import com.kieronquinn.app.pcs.ui.BaseUrlScreen
import com.kieronquinn.app.pcs.ui.screens.buildlabel.BuildLabelScreen
import com.kieronquinn.app.pcs.ui.screens.contributors.ContributorsScreen
import com.kieronquinn.app.pcs.ui.screens.error.ErrorScreen
import com.kieronquinn.app.pcs.ui.screens.experiments.ExperimentsScreen
import com.kieronquinn.app.pcs.ui.screens.faq.FaqScreen
import com.kieronquinn.app.pcs.ui.screens.libraries.LibrariesScreen
import com.kieronquinn.app.pcs.ui.screens.loading.LoadingScreen
import com.kieronquinn.app.pcs.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

interface NavigationRepository {

    val navigationEvent: Flow<NavigationEvent>

    suspend fun navigateTo(destination: Destination, clear: Boolean = false)

    fun getNavEntry(
        destination: Destination,
        onMenuItemSelected: Flow<MenuItem>
    ): NavEntry<Destination>

    sealed class Destination(
        @StringRes open val title: Int = R.string.app_name,
        var appBarOffset: Float = 0f,
        val options: List<MenuItem> = emptyList()
    ) {
        data object Loading: Destination()
        data class Error(val type: Type): Destination(R.string.screen_error_title) {
            enum class Type(@StringRes val message: Int) {
                NO_ROOT(R.string.error_no_root),
                NO_XPOSED(R.string.error_no_xposed)
            }
        }
        data object EnterBaseURL: Destination(R.string.screen_base_url_title)
        data object SelectBuildLabel: Destination(
            R.string.screen_build_label_title,
            options = listOf(
                MenuItem(R.string.screen_build_label_reset)
            )
        )
        data object Settings: Destination()
        data object Experiments: Destination(R.string.screen_settings_experiments_title)
        data object FAQ: Destination(R.string.screen_settings_faq_title)
        data object Libraries: Destination(R.string.screen_settings_footer_libraries)
        data object Contributors: Destination(R.string.screen_settings_footer_contributors)
    }

    data class NavigationEvent(val destination: Destination, val clear: Boolean = false)
    data class MenuItem(@StringRes val text: Int)

}

class NavigationRepositoryImpl: NavigationRepository {

    override val navigationEvent = MutableSharedFlow<NavigationEvent>()

    override suspend fun navigateTo(destination: Destination, clear: Boolean) {
        navigationEvent.emit(NavigationEvent(destination, clear))
    }

    override fun getNavEntry(
        destination: Destination,
        onMenuItemSelected: Flow<MenuItem>
    ): NavEntry<Destination> {
        return when (destination) {
            Destination.Loading -> NavEntry(destination) {
                LoadingScreen()
            }

            is Destination.EnterBaseURL -> NavEntry(destination) {
                BaseUrlScreen()
            }

            is Destination.SelectBuildLabel -> NavEntry(destination) {
                BuildLabelScreen(onMenuItemSelected)
            }

            is Destination.Error -> NavEntry(destination) {
                ErrorScreen(destination.type)
            }

            is Destination.Settings -> NavEntry(destination) {
                SettingsScreen()
            }

            is Destination.Experiments -> NavEntry(destination) {
                ExperimentsScreen()
            }

            is Destination.FAQ -> NavEntry(destination) {
                FaqScreen()
            }

            is Destination.Libraries -> NavEntry(destination) {
                LibrariesScreen()
            }

            is Destination.Contributors -> NavEntry(destination) {
                ContributorsScreen()
            }
        }
    }

}