package com.kieronquinn.app.pcs.utils.extensions

import com.google.common.hash.Hashing

fun ByteArray.sha256AsHex(): String {
    return Hashing.sha256().hashBytes(this).asBytes().toHexString()
}