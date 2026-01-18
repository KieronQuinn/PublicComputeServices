package com.kieronquinn.app.pcs.ui.screens.libraries

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
fun LibrariesScreen() {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val inset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    LibrariesContainer(
        libraries = libraries,
        modifier = Modifier.fillMaxSize()
            .horizontalDisplayCutoutPadding(LocalResources.current.configuration.orientation),
        contentPadding = PaddingValues(bottom = inset)
    )
}

@Preview(name = "Libraries Light")
@Composable
private fun LibrariesPreviewLight() {
    PcsTheme {
        LibrariesScreen()
    }
}

@Preview(name = "Libraries Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LibrariesPreviewDark() {
    LibrariesPreviewLight()
}