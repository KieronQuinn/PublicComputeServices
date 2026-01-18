package com.kieronquinn.app.pcs.model

import com.google.android.`as`.oss.pd.api.proto.BlobConstraints.DeviceTier
import com.google.android.`as`.oss.pd.api.proto.BlobConstraints.Variant

enum class BuildLabel(
    val device: DeviceTier,
    val variant: Variant,
    val devices: List<String> = emptyList()
) {

    PIXEL_MID(DeviceTier.MID, Variant.PIXEL),

    SAMSUNG_QC_MID(DeviceTier.MID, Variant.SAMSUNG_QC, devices = listOf("Galaxy S24 Ultra", "Galaxy Z Fold 6")),
    SAMSUNG_QC_HIGH(DeviceTier.HIGH, Variant.SAMSUNG_QC),

    SAMSUNG_SLSI_MID(DeviceTier.MID, Variant.SAMSUNG_SLSI, devices = listOf("Galaxy S24 FE")),

    VARIANT_6_MID(DeviceTier.MID, Variant.VARIANT_6),

    VARIANT_7_LOW(DeviceTier.LOW, Variant.VARIANT_7),
    VARIANT_7_MID(DeviceTier.MID, Variant.VARIANT_7),
    VARIANT_7_HIGH(DeviceTier.HIGH, Variant.VARIANT_7, devices = listOf("Galaxy Z Fold 7")),

    VARIANT_8_MID(DeviceTier.MID, Variant.VARIANT_8, devices = listOf("Pixel 8", "Pixel 8a")),

    VARIANT_9_MID(DeviceTier.MID, Variant.VARIANT_9, devices = listOf("Pixel 9", "Pixel 9a")),

    VARIANT_11_MID(DeviceTier.MID, Variant.VARIANT_11),
    VARIANT_11_HIGH(DeviceTier.HIGH, Variant.VARIANT_11),

    VARIANT_12_MID(DeviceTier.MID, Variant.VARIANT_12, devices = listOf("Galaxy S25 Ultra")),
    VARIANT_12_HIGH(DeviceTier.HIGH, Variant.VARIANT_12),
    VARIANT_12_ULTRA(DeviceTier.ULTRA, Variant.VARIANT_12),

    VARIANT_13_MID(DeviceTier.MID, Variant.VARIANT_13),
    VARIANT_13_HIGH(DeviceTier.HIGH, Variant.VARIANT_13),

    VARIANT_14_MID(DeviceTier.MID, Variant.VARIANT_14, devices = listOf("Pixel 10")),

    VARIANT_15_MID(DeviceTier.MID, Variant.VARIANT_15, devices = listOf("Galaxy Z Flip 7")),

    VARIANT_16_MID(DeviceTier.MID, Variant.VARIANT_16),

    VARIANT_20_MID(DeviceTier.MID, Variant.VARIANT_20),
    VARIANT_20_HIGH(DeviceTier.HIGH, Variant.VARIANT_20),

    VARIANT_21_MID(DeviceTier.MID, Variant.VARIANT_21),
    VARIANT_22_MID(DeviceTier.MID, Variant.VARIANT_22),

}