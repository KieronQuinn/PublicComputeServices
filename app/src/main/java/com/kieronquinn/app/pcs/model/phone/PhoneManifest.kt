package com.kieronquinn.app.pcs.model.phone

import com.kieronquinn.app.pcs.repositories.BaseSettingsRepository.PcsSetting
import com.kieronquinn.app.pcs.repositories.SettingsRepository

enum class PhoneManifest(val id: String, val setting: (SettingsRepository) -> PcsSetting<String>) {
    DOBBY("dobby", SettingsRepository::phoneDobbyUrl),
    DOBBY_MODELS("dobby-models", SettingsRepository::phoneDobbyDuplexFiles),
    ATLAS_MODELS("atlas-models", SettingsRepository::phoneAtlasModels),
    XATU_MODELS("xatu-models", SettingsRepository::phoneXatuModels),
    BEESLY_MODELS("beesly-models", SettingsRepository::phoneBeesly)
}