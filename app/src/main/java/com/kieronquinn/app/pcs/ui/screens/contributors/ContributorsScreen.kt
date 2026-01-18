package com.kieronquinn.app.pcs.ui.screens.contributors

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.horizontalDisplayCutoutPadding
import com.kieronquinn.app.pcs.utils.extensions.openUrl
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import uk.co.bocajsolutions.cardshape.Shape

@Composable
fun ContributorsScreen() = ProvidePreferenceLocals {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .horizontalDisplayCutoutPadding(LocalConfiguration.current.orientation)
            .padding(horizontal = 8.dp)
    ) {
        val astreaShape = Shape(3, 0)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = astreaShape)
                .clip(astreaShape),
            key = "astrea",
            title = {
                Text(
                    text = stringResource(R.string.screen_contributors_astrea_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = stringResource(R.string.screen_contributors_astrea_content))
            },
            onClick = {
                context.openUrl("https://github.com/google/private-compute-services")
            }
        )
        item {
            Spacer(Modifier.height(2.dp))
        }
        val iconsShape = Shape(3, 1)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = iconsShape)
                .clip(iconsShape),
            key = "icons",
            title = {
                Text(
                    text = stringResource(R.string.screen_contributors_icons_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = stringResource(R.string.screen_contributors_icons_content))
            },
            onClick = {
                context.openUrl("https://icons8.com")
            }
        )
        item {
            Spacer(Modifier.height(2.dp))
        }
        val fontShape = Shape(3, 2)
        preference(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(color = surface, shape = fontShape)
                .clip(fontShape),
            key = "font",
            title = {
                Text(
                    text = stringResource(R.string.screen_contributors_google_sans_title),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            summary = {
                Text(text = stringResource(R.string.screen_contributors_google_sans_content))
            },
            onClick = {
                context.openUrl("https://fonts.google.com/specimen/Google+Sans+Flex/license")
            }
        )
    }
}

@Preview(name = "Contributors Light")
@Composable
private fun ContributorsPreviewLight() {
    PcsTheme {
        ContributorsScreen()
    }
}

@Preview(name = "Contributors Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContributorsPreviewDark() {
    ContributorsPreviewLight()
}