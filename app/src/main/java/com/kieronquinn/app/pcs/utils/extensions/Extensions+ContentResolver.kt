package com.kieronquinn.app.pcs.utils.extensions

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.DeadObjectException

fun ContentResolver.callSafely(
    uri: Uri,
    method: String,
    arg: String?,
    extras: Bundle?
): Bundle? {
    val client = acquireUnstableContentProviderClient(uri) ?: return null
    return try {
        client.call(method, arg, extras).also {
            client.close()
        }
    }catch (e: DeadObjectException){
        null
    }
}