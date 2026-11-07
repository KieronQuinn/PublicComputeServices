package com.kieronquinn.app.pcs.utils.extensions

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.DeadObjectException
import android.util.Log

fun ContentResolver.callSafely(
    uri: Uri,
    method: String,
    arg: String?,
    extras: Bundle?
): Bundle? {
    val client = acquireUnstableContentProviderClient(uri) ?: run {
        Log.w("PcsContentResolver", "Provider unavailable: ${uri.authority}")
        return null
    }
    return try {
        client.call(method, arg, extras)
    }catch (e: DeadObjectException){
        Log.w("PcsContentResolver", "Provider died during $method: ${uri.authority}", e)
        null
    } finally {
        client.close()
    }
}
