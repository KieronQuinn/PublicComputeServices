package com.kieronquinn.app.pcs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun PcsTheme(block: @Composable () -> Unit) {
    val colours = if(isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    }else{
        dynamicLightColorScheme(LocalContext.current)
    }
    val typography = pcsTypography()
    MaterialTheme(colorScheme = colours, typography = typography) {
        block()
    }
}