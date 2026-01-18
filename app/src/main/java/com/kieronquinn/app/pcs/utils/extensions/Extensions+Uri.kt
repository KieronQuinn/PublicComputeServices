package com.kieronquinn.app.pcs.utils.extensions

import android.net.Uri
import androidx.core.net.toUri

fun String.toUriOrNull(): Uri? {
    return try {
        toUri()
    }catch (e: Exception) {
        null
    }
}