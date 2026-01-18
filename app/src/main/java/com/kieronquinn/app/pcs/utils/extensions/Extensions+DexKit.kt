package com.kieronquinn.app.pcs.utils.extensions

import org.luckypray.dexkit.DexKitBridge

private var dexKitBridge: DexKitBridge? = null

@Synchronized
fun loadDexKit(sourceDir: String): DexKitBridge {
    return dexKitBridge ?: run {
        System.loadLibrary("dexkit")
        DexKitBridge.create(sourceDir).also {
            dexKitBridge = it
        }
    }
}