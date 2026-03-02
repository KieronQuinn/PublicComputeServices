package com.kieronquinn.app.pcs.repositories

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.repositories.BaseSettingsRepository.PcsSetting
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import java.util.Locale

interface SettingsRepository: BaseSettingsRepository {

    val autoSync: PcsSetting<Boolean>

    // Phone Flags
    val phoneSharpieEnabled: PcsSetting<Boolean>
    val phoneDobbyEnabled: PcsSetting<Boolean>
    val phoneDobbyUrl: PcsSetting<String>
    val phoneDobbyDuplexFiles: PcsSetting<String>
    val phoneDobbyRegion: PcsSetting<DobbyRegion>
    val phoneAtlasEnabled: PcsSetting<Boolean>
    val phoneAtlasModels: PcsSetting<String>
    val phoneBeeslyEnabled: PcsSetting<Boolean>
    val phoneBeesly: PcsSetting<String>
    val phoneBeeslyRegion: PcsSetting<BeeslyRegion>
    val phoneNautilusEnabled: PcsSetting<Boolean>
    val phoneSonicEnabled: PcsSetting<Boolean>
    val phoneXatuEnabled: PcsSetting<Boolean>
    val phoneXatuModels: PcsSetting<String>
    val phoneCallerTagsEnabled: PcsSetting<Boolean>
    val phoneFermatEnabled: PcsSetting<Boolean>
    val phoneExpressoEnabled: PcsSetting<Boolean>
    val phonePatrickPhase: PcsSetting<PatrickPhase>
    val phoneCallRecordingEnabled: PcsSetting<Boolean>

    enum class PatrickPhase(@StringRes val label: Int) {
        DISABLED(R.string.screen_experiments_phone_feature_patrick_disabled),
        PHASE_ONE(R.string.screen_experiments_phone_feature_patrick_phase_one),
        PHASE_TWO(R.string.screen_experiments_phone_feature_patrick_phase_two)
    }

    enum class DobbyRegion(@StringRes val label: Int, val locale: String) {
        US(R.string.screen_experiments_phone_feature_dobby_region_us, "en-US"),
        GB(R.string.screen_experiments_phone_feature_dobby_region_gb, "en-GB"),
        CA(R.string.screen_experiments_phone_feature_dobby_region_ca, "en-US"), // Uses US
        IE(R.string.screen_experiments_phone_feature_dobby_region_ie, "en-GB"), // Uses UK
        JP(R.string.screen_experiments_phone_feature_dobby_region_jp, "ja-JP"),
        AU(R.string.screen_experiments_phone_feature_dobby_region_au, "en-AU"),
        IN(R.string.screen_experiments_phone_feature_dobby_region_in, getIndiaLocale());

        companion object {
            fun getDefault(): DobbyRegion {
                val country = Locale.getDefault().country
                return entries.firstOrNull { it.name == country } ?: US
            }
        }
    }

    enum class BeeslyRegion(@StringRes val label: Int, val locale: String) {
        US(R.string.screen_experiments_phone_feature_beesly_region_us, "en-US"),
        CA(R.string.screen_experiments_phone_feature_beesly_region_ca, "en-US"), // Uses US
        GB(R.string.screen_experiments_phone_feature_beesly_region_gb, "en-GB"),
        IE(R.string.screen_experiments_phone_feature_beesly_region_ie, "en-US"), // Uses US
        AU(R.string.screen_experiments_phone_feature_beesly_region_au, "en-US"); // Uses US

        companion object {
            fun getDefault(): BeeslyRegion {
                val country = Locale.getDefault().country
                return entries.firstOrNull { it.name == country } ?: US
            }
        }
    }

    companion object {
        private fun getIndiaLocale(): String {
            return if (Locale.getDefault().language == "hi") {
                "hi-IN"
            } else{
                "en-IN"
            }
        }
    }

}

class SettingsRepositoryImpl(
    context: Context
): BaseSettingsRepositoryImpl(), SettingsRepository {

    companion object {
        private const val SHARED_PREFS_NAME = "${BuildConfig.APPLICATION_ID}_prefs"

        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val DEFAULT_AUTO_SYNC = false

        private const val KEY_PHONE_SHARPIE_ENABLED = "phone_sharpie_enabled"
        private const val DEFAULT_PHONE_SHARPIE_ENABLED = false

        private const val KEY_PHONE_DOBBY_ENABLED = "phone_dobby_enabled"
        private const val DEFAULT_PHONE_DOBBY_ENABLED = false

        private const val KEY_PHONE_DOBBY_URL = "phone_dobby_url"
        private const val DEFAULT_PHONE_DOBBY_URL = ""

        private const val KEY_PHONE_DOBBY_DUPLEX_FILES = "phone_dobby_duplex_files"
        private const val DEFAULT_PHONE_DOBBY_DUPLEX_FILES = ""

        private const val KEY_PHONE_DOBBY_REGION = "phone_dobby_region"
        private val DEFAULT_PHONE_DOBBY_REGION = SettingsRepository.DobbyRegion.getDefault()

        private const val KEY_PHONE_ATLAS_ENABLED = "phone_atlas_enabled"
        private const val DEFAULT_PHONE_ATLAS_ENABLED = false

        private const val KEY_PHONE_ATLAS_MODELS = "phone_atlas_models"
        private const val DEFAULT_PHONE_ATLAS_MODELS = ""

        private const val KEY_PHONE_BEESLY_ENABLED = "phone_beesly_enabled"
        private const val DEFAULT_PHONE_BEESLY_ENABLED = false

        private const val KEY_PHONE_BEESLY = "phone_beesly"
        private const val DEFAULT_PHONE_BEESLY = ""

        private const val KEY_PHONE_BEESLY_REGION = "phone_beesly_region"
        private val DEFAULT_PHONE_BEESLY_REGION = SettingsRepository.BeeslyRegion.getDefault()

        private const val KEY_PHONE_NAUTILUS_ENABLED = "phone_nautilus_enabled"
        private const val DEFAULT_PHONE_NAUTILUS_ENABLED = false

        private const val KEY_PHONE_SONIC_ENABLED = "phone_sonic_enabled"
        private const val DEFAULT_PHONE_SONIC_ENABLED = false

        private const val KEY_PHONE_XATU_ENABLED = "phone_xatu_enabled"
        private const val DEFAULT_PHONE_XATU_ENABLED = false

        private const val KEY_PHONE_XATU_MODELS = "phone_xatu_models"
        private const val DEFAULT_PHONE_XATU_MODELS = ""

        private const val KEY_PHONE_CALLER_TAGS_ENABLED = "phone_caller_tags_enabled"
        private const val DEFAULT_PHONE_CALLER_TAGS_ENABLED = false

        private const val KEY_PHONE_FERMAT_ENABLED = "phone_fermat_enabled"
        private const val DEFAULT_PHONE_FERMAT_ENABLED = false

        private const val KEY_PHONE_EXPRESSO_ENABLED = "phone_expresso_enabled"
        private const val DEFAULT_PHONE_EXPRESSO_ENABLED = false

        private const val KEY_PHONE_PATRICK_PHASE = "phone_patrick_phase"
        private val DEFAULT_PHONE_PATRICK_PHASE = PatrickPhase.DISABLED

        private const val KEY_PHONE_CALL_RECORDING_ENABLED = "phone_call_recording_enabled"
        private const val DEFAULT_PHONE_CALL_RECORDING_ENABLED = false
    }

    override val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            SHARED_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    override val autoSync = boolean(KEY_AUTO_SYNC, DEFAULT_AUTO_SYNC)

    override val phoneSharpieEnabled = boolean(KEY_PHONE_SHARPIE_ENABLED, DEFAULT_PHONE_SHARPIE_ENABLED)
    override val phoneDobbyEnabled = boolean(KEY_PHONE_DOBBY_ENABLED, DEFAULT_PHONE_DOBBY_ENABLED)
    override val phoneDobbyUrl = string(KEY_PHONE_DOBBY_URL, DEFAULT_PHONE_DOBBY_URL)
    override val phoneDobbyDuplexFiles = string(KEY_PHONE_DOBBY_DUPLEX_FILES, DEFAULT_PHONE_DOBBY_DUPLEX_FILES)
    override val phoneDobbyRegion = enum(KEY_PHONE_DOBBY_REGION, DEFAULT_PHONE_DOBBY_REGION)
    override val phoneAtlasEnabled = boolean(KEY_PHONE_ATLAS_ENABLED, DEFAULT_PHONE_ATLAS_ENABLED)
    override val phoneAtlasModels = string(KEY_PHONE_ATLAS_MODELS, DEFAULT_PHONE_ATLAS_MODELS)
    override val phoneBeeslyEnabled = boolean(KEY_PHONE_BEESLY_ENABLED, DEFAULT_PHONE_BEESLY_ENABLED)
    override val phoneBeesly = string(KEY_PHONE_BEESLY, DEFAULT_PHONE_BEESLY)
    override val phoneBeeslyRegion = enum(KEY_PHONE_BEESLY_REGION, DEFAULT_PHONE_BEESLY_REGION)
    override val phoneNautilusEnabled = boolean(KEY_PHONE_NAUTILUS_ENABLED, DEFAULT_PHONE_NAUTILUS_ENABLED)
    override val phoneSonicEnabled = boolean(KEY_PHONE_SONIC_ENABLED, DEFAULT_PHONE_SONIC_ENABLED)
    override val phoneXatuEnabled = boolean(KEY_PHONE_XATU_ENABLED, DEFAULT_PHONE_XATU_ENABLED)
    override val phoneXatuModels = string(KEY_PHONE_XATU_MODELS, DEFAULT_PHONE_XATU_MODELS)
    override val phoneCallerTagsEnabled = boolean(KEY_PHONE_CALLER_TAGS_ENABLED, DEFAULT_PHONE_CALLER_TAGS_ENABLED)
    override val phoneFermatEnabled = boolean(KEY_PHONE_FERMAT_ENABLED, DEFAULT_PHONE_FERMAT_ENABLED)
    override val phoneExpressoEnabled = boolean(KEY_PHONE_EXPRESSO_ENABLED, DEFAULT_PHONE_EXPRESSO_ENABLED)
    override val phonePatrickPhase = enum(KEY_PHONE_PATRICK_PHASE, DEFAULT_PHONE_PATRICK_PHASE)
    override val phoneCallRecordingEnabled = boolean(KEY_PHONE_CALL_RECORDING_ENABLED, DEFAULT_PHONE_CALL_RECORDING_ENABLED)

}
