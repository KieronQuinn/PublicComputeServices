package com.kieronquinn.app.pcs

import android.app.Application
import com.google.crypto.tink.hybrid.HybridConfig
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepositoryImpl
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.ManifestRepositoryImpl
import com.kieronquinn.app.pcs.repositories.NavigationRepository
import com.kieronquinn.app.pcs.repositories.NavigationRepositoryImpl
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepositoryImpl
import com.kieronquinn.app.pcs.repositories.PropertiesRepository
import com.kieronquinn.app.pcs.repositories.PropertiesRepositoryImpl
import com.kieronquinn.app.pcs.repositories.SettingsRepository
import com.kieronquinn.app.pcs.repositories.SettingsRepositoryImpl
import com.kieronquinn.app.pcs.repositories.SyncRepository
import com.kieronquinn.app.pcs.repositories.SyncRepositoryImpl
import com.kieronquinn.app.pcs.repositories.UpdateRepository
import com.kieronquinn.app.pcs.repositories.UpdateRepositoryImpl
import com.kieronquinn.app.pcs.repositories.XposedRepository
import com.kieronquinn.app.pcs.repositories.XposedRepositoryImpl
import com.kieronquinn.app.pcs.ui.screens.baseurl.BaseUrlViewModel
import com.kieronquinn.app.pcs.ui.screens.baseurl.BaseUrlViewModelImpl
import com.kieronquinn.app.pcs.ui.screens.baseurl.dialog.BaseUrlDialogViewModel
import com.kieronquinn.app.pcs.ui.screens.baseurl.dialog.BaseUrlDialogViewModelImpl
import com.kieronquinn.app.pcs.ui.screens.buildlabel.BuildLabelViewModel
import com.kieronquinn.app.pcs.ui.screens.buildlabel.BuildLabelViewModelImpl
import com.kieronquinn.app.pcs.ui.screens.container.ContainerViewModel
import com.kieronquinn.app.pcs.ui.screens.container.ContainerViewModelImpl
import com.kieronquinn.app.pcs.ui.screens.error.ErrorScreenViewModel
import com.kieronquinn.app.pcs.ui.screens.error.ErrorScreenViewModelImpl
import com.kieronquinn.app.pcs.ui.screens.experiments.ExperimentsViewModel
import com.kieronquinn.app.pcs.ui.screens.experiments.ExperimentsViewModelImpl
import com.kieronquinn.app.pcs.ui.screens.settings.SettingsViewModel
import com.kieronquinn.app.pcs.ui.screens.settings.SettingsViewModelImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PcsApplication: Application() {

    companion object {
        const val PACKAGE_NAME_PCS = "com.google.android.as.oss"
        const val PACKAGE_NAME_PHONE = "com.google.android.dialer"
        const val PACKAGE_NAME_PSI = "com.google.android.apps.pixel.psi"
        const val PACKAGE_NAME_TTS = "com.google.android.tts"
        const val PACKAGE_NAME_AS = "com.google.android.as"
        const val PACKAGE_NAME_AIC = "com.google.android.aicore"
    }

    override fun onCreate() {
        super.onCreate()
        // Only run extra stuff on main process
        if (getProcessName() != BuildConfig.APPLICATION_ID) return
        System.loadLibrary("sekret")
        HybridConfig.register()
        startKoin {
            androidContext(this@PcsApplication)
            modules(repositories(), viewModels())
        }
    }

    private fun repositories() = module {
        single { getRetrofit() }
        single<ManifestRepository>(createdAtStart = true) {
            ManifestRepositoryImpl(get(), get(), get())
        }
        single<DeviceConfigPropertiesRepository> { DeviceConfigPropertiesRepositoryImpl(get()) }
        single<PhenotypeRepository>(createdAtStart = true) {
            PhenotypeRepositoryImpl(get())
        }
        single<PropertiesRepository> {
            PropertiesRepositoryImpl(get())
        }
        single<XposedRepository>(createdAtStart = true) { XposedRepositoryImpl(get()) }
        single<SyncRepository> {
            SyncRepositoryImpl(get(), get(), get(), get())
        }
        single<NavigationRepository> {
            NavigationRepositoryImpl()
        }
        single<UpdateRepository> { UpdateRepositoryImpl(get(), get()) }
        single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    }

    private fun viewModels() = module {
        viewModel<ContainerViewModel> { ContainerViewModelImpl(get(), get(), get()) }
        viewModel<ErrorScreenViewModel> { ErrorScreenViewModelImpl(get(), get()) }
        viewModel<BaseUrlViewModel> { BaseUrlViewModelImpl(get()) }
        viewModel<BaseUrlDialogViewModel> { BaseUrlDialogViewModelImpl(get(), get()) }
        viewModel<BuildLabelViewModel> { BuildLabelViewModelImpl(get()) }
        viewModel<SettingsViewModel> { SettingsViewModelImpl(get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel<ExperimentsViewModel> { ExperimentsViewModelImpl(get(), get(), get(), get(), get()) }
    }

    private fun getRetrofit() = Retrofit.Builder()
        .baseUrl("http://localhost")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

}