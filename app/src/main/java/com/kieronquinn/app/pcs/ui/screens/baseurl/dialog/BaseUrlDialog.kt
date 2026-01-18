package com.kieronquinn.app.pcs.ui.screens.baseurl.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.utils.extensions.textResource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun BaseUrlDialog(initialUrl: String?, onDismiss: () -> Unit) {
    val viewModel = koinViewModel<BaseUrlDialogViewModel>()
    EnterURLDialog(
        initialUrl = initialUrl ?: "",
        onDismiss = onDismiss,
        onSuccess = { validatedUrl ->
            onDismiss()
            viewModel.setUrl(validatedUrl)
        },
        validate = { url ->
            viewModel.checkManifestUrl(url.trim())
        }
    )
}

@Composable
private fun EnterURLDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
    validate: suspend (String) -> Boolean
) {
    val scope = rememberCoroutineScope()

    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (hasError) {
            hasError = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.screen_base_url_dialog_title),
                textAlign = TextAlign.Start
            )
        },
        text = {
            Column {
                Text(text = textResource(R.string.screen_base_url_dialog_content))
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    isError = hasError,
                    supportingText = {
                        if (hasError) {
                            Text(stringResource(R.string.screen_base_url_dialog_error))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isLoading = true
                    scope.launch {
                        val isValid = validate(url)
                        if (isValid) {
                            onSuccess(url)
                        } else {
                            hasError = true
                        }
                        isLoading = false
                    }
                },
                enabled = url.isNotBlank() && !isLoading
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}