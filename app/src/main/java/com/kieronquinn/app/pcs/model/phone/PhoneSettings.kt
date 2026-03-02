package com.kieronquinn.app.pcs.model.phone

import android.os.Parcelable
import com.kieronquinn.app.pcs.repositories.SettingsRepository
import kotlinx.parcelize.Parcelize

@Parcelize
data class PhoneSettings(
    val sharpieEnabled: Boolean,
    val dobbyEnabled: Boolean,
    val dobbyUrl: String?,
    val dobbyDuplexFiles: String?,
    val dobbyRegion: SettingsRepository.DobbyRegion,
    val atlasEnabled: Boolean,
    val atlasModels: String?,
    val beeslyEnabled: Boolean,
    val beesly: String?,
    val beeslyRegion: SettingsRepository.BeeslyRegion,
    val nautilusEnabled: Boolean,
    val sonicEnabled: Boolean,
    val xatuEnabled: Boolean,
    val xatuModels: String?,
    val callerTagsEnabled: Boolean,
    val fermatEnabled: Boolean,
    val expressoEnabled: Boolean,
    val patrickPhase: SettingsRepository.PatrickPhase,
    val callRecordingEnabled: Boolean
): Parcelable