package com.kieronquinn.app.pcs.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.ui.theme.PcsTheme

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    content: String
) {
    InfoCard(modifier, icon) {
        Text(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically),
            text = content,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    content: AnnotatedString
) {
    InfoCard(modifier, icon) {
        Text(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically),
            text = content,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    text: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                modifier = Modifier.size(32.dp).align(Alignment.CenterVertically),
                painter = painterResource(id = icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null
            )
            Spacer(modifier = Modifier.size(16.dp))
            text()
        }
    }
}

@Preview(name = "Info Card Light")
@Composable
fun InfoCardPreviewLight() {
    PcsTheme {
        InfoCard(icon = R.drawable.ic_info, content = "Test")
    }
}

@Preview(name = "Info Card Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun InfoCardPreviewDark() {
    PcsTheme {
        InfoCard(icon = R.drawable.ic_info, content = "Test")
    }
}