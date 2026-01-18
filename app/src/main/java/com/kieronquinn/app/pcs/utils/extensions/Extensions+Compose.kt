package com.kieronquinn.app.pcs.utils.extensions

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.core.text.toSpanned
import com.aghajari.compose.text.asAnnotatedString
import me.zhanghai.compose.preference.SwitchPreference

@Composable
fun textResource(@StringRes id: Int): AnnotatedString {
    return LocalResources.current.getText(id).toSpanned().asAnnotatedString().annotatedString
}

fun Modifier.horizontalDisplayCutoutPadding(orientation: Int): Modifier {
    return if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
        this.displayCutoutPadding()
    }else this
}

inline fun LazyListScope.switchPreference(
    key: String,
    value: Boolean,
    crossinline title: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    crossinline enabled: (Boolean) -> Boolean = { true },
    noinline icon: @Composable ((Boolean) -> Unit)? = null,
    noinline summary: @Composable ((Boolean) -> Unit)? = null,
    noinline onValueChange: (Boolean) -> Unit
) {
    item(key = key, contentType = "SwitchPreference") {
        SwitchPreference(
            value = value,
            title = { title(value) },
            modifier = modifier,
            enabled = enabled(value),
            icon = icon?.let { { it(value) } },
            summary = summary?.let { { it(value) } },
            onValueChange = onValueChange
        )
    }
}