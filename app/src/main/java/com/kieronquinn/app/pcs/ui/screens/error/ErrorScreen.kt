package com.kieronquinn.app.pcs.ui.screens.error

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination.Error.Type
import com.kieronquinn.app.pcs.ui.components.InfoCard
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import org.koin.androidx.compose.koinViewModel

private data class Interactions (
    val onRetry: () -> Unit
) {
    companion object {
        val PREVIEW = Interactions(
            onRetry = {}
        )
    }
}

@Composable
fun ErrorScreen(type: Type) {
    val viewModel = koinViewModel<ErrorScreenViewModel>()
    val isLoading by viewModel.isLoading.collectAsState()
    val interactions = Interactions (
        onRetry = viewModel::onRetryClicked
    )
    ErrorScreenContent(type, isLoading, interactions)
}

@Composable
private fun ErrorScreenContent(type: Type, isLoading: Boolean, interactions: Interactions) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
            .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
    ) {
        InfoCard(
            Modifier.padding(horizontal = 16.dp),
            R.drawable.ic_error,
            stringResource(type.message)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .align(Alignment.CenterHorizontally),
            onClick = interactions.onRetry,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.error_retry))
        }
    }
}

@Preview(name = "Error Screen Light")
@Composable
private fun ErrorScreenPreviewLight() {
    PcsTheme {
        ErrorScreenContent(Type.NO_ROOT, true, Interactions.PREVIEW)
    }
}

@Preview(name = "Error Screen Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ErrorScreenPreviewDark() {
    PcsTheme {
        ErrorScreenContent(Type.NO_ROOT, false, Interactions.PREVIEW)
    }
}