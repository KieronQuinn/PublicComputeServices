package com.kieronquinn.app.pcs.ui.theme

import android.content.res.AssetManager
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

private const val GOOGLE_SANS_FLEX = "fonts/google_sans_flex.ttf"
private const val GOOGLE_SANS_MONO = "fonts/google_sans_mono.ttf"

@OptIn(ExperimentalTextApi::class)
@Composable
private fun googleSansFlex(assetManager: AssetManager) =
    FontFamily(
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.Thin),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.ExtraLight),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.Light),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.Normal),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.Medium),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.SemiBold),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.Bold),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.ExtraBold),
        pcsFont(GOOGLE_SANS_FLEX, assetManager, weight = FontWeight.Black)
    )

@OptIn(ExperimentalTextApi::class)
@Composable
private fun pcsFont(
    path: String,
    assetManager: AssetManager,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
) = Font(
    path,
    assetManager,
    weight = weight,
    variationSettings = FontVariation.Settings(
        weight, style, FontVariation.Setting("ROND", 100f)
    )
)

@Composable
fun pcsTypography(): Typography {
    val assets = LocalContext.current.assets
    val googleSansFlex = googleSansFlex(assets)
    val defaultTypography = MaterialTheme.typography
    return MaterialTheme.typography.copy(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = googleSansFlex),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = googleSansFlex),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = googleSansFlex),
        headlineLarge = defaultTypography.headlineLarge.copy(
            fontFamily = googleSansFlex,
            fontWeight = FontWeight.SemiBold
        ),
        headlineMedium = defaultTypography.headlineMedium.copy(
            fontFamily = googleSansFlex,
            fontWeight = FontWeight.SemiBold
        ),
        headlineSmall = defaultTypography.headlineSmall.copy(
            fontFamily = googleSansFlex,
            fontWeight = FontWeight.SemiBold
        ),
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = googleSansFlex),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = googleSansFlex),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = googleSansFlex),
        bodyLarge = defaultTypography.bodyLarge.copy(
            fontFamily = googleSansFlex,
            fontWeight = FontWeight.Medium
        ),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = googleSansFlex),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = googleSansFlex),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = googleSansFlex),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = googleSansFlex),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = googleSansFlex)
    )
}

fun AssetManager.googleSansFlex(): Typeface {
    return Typeface.Builder(this, GOOGLE_SANS_FLEX)
        .setFontVariationSettings("'ROND' 100")
        .build()
}

fun AssetManager.googleSansMono(): Typeface {
    return Typeface.createFromAsset(this, GOOGLE_SANS_MONO)
}