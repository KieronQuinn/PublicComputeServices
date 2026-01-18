package com.kieronquinn.app.pcs.utils.extensions

import android.content.Context
import android.content.pm.PackageManager
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.sekret.Sekret

fun Context.getManifestKey(): KeysetHandle {
    val key = Sekret.manifestKey(getSignatureHash().toHexString())?.hexToByteArray()
    return key?.toKeysetHandle() ?: throw IllegalStateException("Unable to load manifest key")
}

fun ByteArray.toKeysetHandle(): KeysetHandle {
    return CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(this))
}

/**
 *  Decrypt a manifest with a given keyset handle. If a [Context] is passed, the signature of the
 *  **module** will be used as the context info, if `null` then an empty array will be used.
 */
fun ByteArray.decryptManifest(context: Context?, key: KeysetHandle): ByteArray {
    val contextInfo = context?.getSignatureHash() ?: byteArrayOf()
    return key.getPrimitive(
        RegistryConfiguration.get(),
        HybridDecrypt::class.java
    ).decrypt(this, contextInfo)
}

fun Context.getSignatureHash(): ByteArray {
    return packageManager.getPackageInfo(
        BuildConfig.APPLICATION_ID,
        PackageManager.GET_SIGNING_CERTIFICATES
    ).signingInfo?.let {
        val signatures = it.apkContentsSigners
        if (!signatures.isNullOrEmpty()) {
            signatures[0].toByteArray()
        } else {
            it.signingCertificateHistory?.get(0)?.toByteArray()
        }
    } ?: throw IllegalStateException("No signing certificate found")
}