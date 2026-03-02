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
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
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

inline fun <T> LazyListScope.listPreference(
    key: String,
    value: T,
    values: List<T>,
    noinline title: @Composable (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    crossinline enabled: (T) -> Boolean = { true },
    noinline icon: @Composable ((T) -> Unit)? = null,
    noinline summary: @Composable ((T) -> Unit)? = null,
    type: ListPreferenceType = ListPreferenceType.ALERT_DIALOG,
    noinline valueToText: (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
    noinline onValueChange: (T) -> Unit
) {
    item(key = key, contentType = "ListPreference") {
        ListPreference(
            value = value,
            onValueChange = onValueChange,
            values = values,
            title = { title(value) },
            modifier = modifier,
            enabled = enabled(value),
            icon = icon?.let { { it(value) } },
            summary = summary?.let { { it(value) } },
            type = type,
            valueToText = valueToText
        )
    }
}