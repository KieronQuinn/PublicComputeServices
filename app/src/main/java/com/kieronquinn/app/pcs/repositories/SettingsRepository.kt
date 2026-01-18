package com.kieronquinn.app.pcs.repositories

import android.content.Context
import android.content.SharedPreferences
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.repositories.BaseSettingsRepository.PcsSetting

interface SettingsRepository: BaseSettingsRepository {

    val autoSync: PcsSetting<Boolean>

}

class SettingsRepositoryImpl(
    context: Context
): BaseSettingsRepositoryImpl(), SettingsRepository {

    companion object {
        private const val SHARED_PREFS_NAME = "${BuildConfig.APPLICATION_ID}_prefs"

        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val DEFAULT_AUTO_SYNC = false
    }

    override val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            SHARED_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    override val autoSync = boolean(KEY_AUTO_SYNC, DEFAULT_AUTO_SYNC)


}