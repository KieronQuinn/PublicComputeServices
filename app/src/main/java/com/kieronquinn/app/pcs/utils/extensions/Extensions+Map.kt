package com.kieronquinn.app.pcs.utils.extensions

fun <A, B> Map<A, B>.getKeyByValue(value: B): A? {
    return entries.firstOrNull { it.value == value }?.key
}