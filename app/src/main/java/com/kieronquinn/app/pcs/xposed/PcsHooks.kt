package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT_PCS

object PcsHooks: GrpcHooks() {

    override val tag = "PcsHooks"
    override val applicationClassName =
        "com.google.android.apps.miphone.astrea.PrivateComputeServicesApplication"
    override val serviceClassName = "com.google.android.apps.miphone.astrea.grpc.AstreaGrpcService"
    override val port = PORT_PCS

}