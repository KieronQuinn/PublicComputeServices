package com.kieronquinn.app.pcs.grpc

import android.util.Log
import com.google.android.`as`.oss.pd.manifest.api.proto.GetManifestConfigRequest
import com.google.android.`as`.oss.pd.manifest.api.proto.GetManifestConfigResponse
import com.google.android.`as`.oss.pd.manifest.api.proto.ManifestTransformResult
import com.google.android.`as`.oss.pd.manifest.api.proto.ProtectedDownloadServiceGrpc
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.RegistryConfiguration
import com.google.protobuf.ByteString
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepositoryImpl.Companion.FLAG_REPOSITORY
import com.kieronquinn.app.pcs.utils.extensions.DeviceConfig_getString
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.buildId
import com.kieronquinn.app.pcs.utils.extensions.client
import com.kieronquinn.app.pcs.utils.extensions.clientGroup
import com.kieronquinn.app.pcs.utils.extensions.deviceTier
import com.kieronquinn.app.pcs.utils.extensions.toKeysetHandle
import com.kieronquinn.app.pcs.utils.extensions.variant
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 *  Custom manifest downloader. This service implements the same gRPC call as Astrea, but calls out
 *  to the specified manifest repository, decrypts the manifest with the known key, and then
 *  re-encrypts it with the key provided in the call and sends it back to the caller.
 */
class ProtectedDownloadGrpcService: ProtectedDownloadServiceGrpc.ProtectedDownloadServiceImplBase(), KoinComponent {

    private val scope = MainScope()
    private val manifestRepository by inject<ManifestRepository>()

    override fun getManifestConfig(
        request: GetManifestConfigRequest,
        responseObserver: StreamObserver<GetManifestConfigResponse>
    ) {
        if (SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)) {
            request.log()
        }
        scope.launch(Dispatchers.IO) {
            val url = DeviceConfig_getString(
                PcsClient.BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES.value,
                FLAG_REPOSITORY,
                null
            ) ?: run {
                Log.e("AstreaServiceError", "No URL set, unable to download manifest")
                responseObserver.onError(Throwable())
                return@launch
            }
            val manifest = manifestRepository.getManifest(url, request)
            if (manifest == null) {
                Log.e("AstreaServiceError", "No suitable manifest found for ${request.constraints.client}")
                responseObserver.onError(Throwable())
                return@launch
            }
            val key = request.cryptoKeys.publicKey.toByteArray().toKeysetHandle()
            val encrypted = key.getPrimitive(
                RegistryConfiguration.get(),
                HybridEncrypt::class.java
            ).encrypt(manifest, request.constraints.clientId.toByteArray())
            val response = GetManifestConfigResponse.newBuilder()
                .setEncryptedManifestConfig(ByteString.copyFrom(encrypted))
                .setManifestTransformResult(ManifestTransformResult.newBuilder()
                    .setCompressedManifest(false)
                    .build()
                ).build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }
    }
    
    @Synchronized
    private fun GetManifestConfigRequest.log() {
        Log.d("AstreaService", "==== Get Manifest Config Request ====")
        Log.d("AstreaService", "Device tier: ${constraints.deviceTier}")
        Log.d("AstreaService", "Client: ${constraints.client}")
        Log.d("AstreaService", "Client Group: ${constraints.clientGroup}")
        Log.d("AstreaService", "Client Version: ${constraints.clientVersion.version}")
        Log.d("AstreaService", "Variant: ${constraints.variant}")
        Log.d("AstreaService", "Build ID: ${constraints.buildId}")
        Log.d("AstreaService", "Compress: ${manifestTransform.compressManifest}")
        Log.d("AstreaService", "==== End Manifest Config Request ====")
    }

}