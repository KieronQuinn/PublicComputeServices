package com.kieronquinn.app.pcs.ui.screens.faq

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.ui.components.Markwon
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding

@Composable
fun FaqScreen() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Markwon(
            modifier = Modifier
                .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            rawFile = R.raw.faq
        )
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}