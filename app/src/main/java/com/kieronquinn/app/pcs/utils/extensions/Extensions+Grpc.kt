package com.kieronquinn.app.pcs.utils.extensions

import io.grpc.Status
import io.grpc.binder.SecurityPolicy
import io.grpc.binder.ServerSecurityPolicy

/**
 *  We don't have a comprehensive list of services used in Astrea since they get updated
 *  occasionally, so this bodge sets the default policy to allow all services. The upstream service
 *  will then do the actual checks, if they are enabled in the device config.
 */
fun trustAllServerSecurityPolicy(): ServerSecurityPolicy {
    val trustAllPolicy = object : SecurityPolicy() {
        override fun checkAuthorization(uid: Int): Status {
            return Status.OK
        }
    }
    return ServerSecurityPolicy.newBuilder().build().apply {
        ServerSecurityPolicy::class.java.getDeclaredField("defaultPolicy").apply {
            isAccessible = true
        }.set(this, trustAllPolicy)
    }
}