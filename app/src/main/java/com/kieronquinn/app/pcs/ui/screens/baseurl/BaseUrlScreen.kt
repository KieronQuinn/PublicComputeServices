package com.kieronquinn.app.pcs.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.ui.components.InfoCard
import com.kieronquinn.app.pcs.ui.screens.baseurl.BaseUrlViewModel
import com.kieronquinn.app.pcs.ui.screens.baseurl.BaseUrlViewModel.State
import com.kieronquinn.app.pcs.ui.screens.baseurl.dialog.BaseUrlDialog
import com.kieronquinn.app.pcs.ui.screens.loading.LoadingScreen
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.kieronquinn.app.pcs.utils.extensions.textResource
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import org.koin.androidx.compose.koinViewModel
import uk.co.bocajsolutions.cardshape.Shape

@Composable
fun BaseUrlScreen() {
    val viewModel = koinViewModel<BaseUrlViewModel>()
    val state by viewModel.state.collectAsState()
    when (state) {
        State.Loading -> LoadingContent()
        is State.Loaded -> LoadedContent(state as State.Loaded)
    }
}

@Composable
private fun LoadingContent() {
    LoadingScreen()
}

@Composable
private fun LoadedContent(state: State.Loaded) = ProvidePreferenceLocals {
    var showEnterUrlDialog by remember { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surfaceVariant
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
            .navigationBarsPadding()
    ) {
        item {
            InfoCard(
                modifier = Modifier.padding(horizontal = 8.dp),
                icon = R.drawable.ic_info,
                content = textResource(R.string.screen_base_url_repo_info)
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        val shape = Shape(1, 0)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = shape)
                .clip(shape),
            key = "enter_url_preference",
            title = {
                Text(
                    text = stringResource(R.string.screen_base_url_repo_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = state.url ?: stringResource(R.string.screen_base_url_repo_content))
            },
            onClick = {
                showEnterUrlDialog = true
            }
        )
    }

    if (showEnterUrlDialog) {
        BaseUrlDialog(state.url) {
            showEnterUrlDialog = false
        }
    }
}



@Preview(name = "Base URL Content Light")
@Composable
private fun ContentPreviewLight() {
    PcsTheme {
        LoadedContent(State.Loaded(null))
    }
}

@Preview(name = "Base URL Content Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewDark() {
    PcsTheme {
        LoadedContent(State.Loaded(null))
    }
}
