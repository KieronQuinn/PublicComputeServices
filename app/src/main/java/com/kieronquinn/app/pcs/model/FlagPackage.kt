package com.kieronquinn.app.pcs.model

enum class FlagPackage(val packageName: String) {
    DIALER("com.google.android.dialer"),
    DIALER_DIRECTBOOT("com.google.android.dialer.directboot"),
    DIALER_SHARED_DIRECTBOOT("com.google.android.dialershared.directboot"),
    CALL_CORE_SHARED_DIRECTBOOT("com.google.android.callcoreshared.directboot")
}