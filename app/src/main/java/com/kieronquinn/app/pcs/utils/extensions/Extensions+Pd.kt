package com.kieronquinn.app.pcs.utils.extensions

import com.google.android.`as`.oss.pd.api.proto.BlobConstraints
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints.Variant
import com.google.android.`as`.oss.pd.manifest.api.proto.ManifestConfigConstraints
import com.kieronquinn.app.pcs.model.PcsClient

val ManifestConfigConstraints.deviceTier
    get() = labelList.first { it.attribute == "device_tier" }.value.parseDeviceTier()

val ManifestConfigConstraints.clientGroup
    get() = labelList.first { it.attribute == "client_group" }.value.parseClientGroup()

val ManifestConfigConstraints.buildId
    get() = labelList.first { it.attribute == "build_id" }.value.toLong()

val ManifestConfigConstraints.variant
    get() = labelList.firstOrNull { it.attribute == "variant" }?.value?.let { variant ->
        Variant.entries.firstOrNull { v -> v.name == variant }
    } ?: Variant.VARIANT_UNSPECIFIED

val ManifestConfigConstraints.client
    get() = clientId.let { id ->
        PcsClient.entries.firstOrNull { it.clientId == id }
    }

private fun String.parseDeviceTier(): BlobConstraints.DeviceTier {
    return when(this) {
        "Ultra Low" -> BlobConstraints.DeviceTier.ULTRA_LOW
        "Low" -> BlobConstraints.DeviceTier.LOW
        "Mid" -> BlobConstraints.DeviceTier.MID
        "High" -> BlobConstraints.DeviceTier.HIGH
        "Ultra" -> BlobConstraints.DeviceTier.ULTRA
        else -> BlobConstraints.DeviceTier.UNRECOGNIZED
    }
}

private fun String.parseClientGroup(): BlobConstraints.ClientGroup {
    return when(this) {
        "all" -> BlobConstraints.ClientGroup.ALL
        "beta" -> BlobConstraints.ClientGroup.BETA
        "alpha" -> BlobConstraints.ClientGroup.ALPHA
        "third_party_eap" -> BlobConstraints.ClientGroup.THIRD_PARTY_EAP
        "third_party_experimental" -> BlobConstraints.ClientGroup.THIRD_PARTY_EXPERIMENTAL
        else -> BlobConstraints.ClientGroup.UNRECOGNIZED
    }
}