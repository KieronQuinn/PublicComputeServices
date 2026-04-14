package com.kieronquinn.app.pcs.grpc

import android.content.Context
import android.util.Log
import com.google.android.`as`.oss.pd.manifest.api.proto.CryptoKeys
import com.google.android.`as`.oss.pd.manifest.api.proto.GetManifestConfigRequest
import com.google.android.`as`.oss.pd.manifest.api.proto.GetManifestConfigResponse
import com.google.android.`as`.oss.pd.manifest.api.proto.ManifestTransformResult
import com.google.android.`as`.oss.pd.manifest.api.proto.ProtectedDownloadServiceGrpc
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.RegistryConfiguration
import com.google.protobuf.ByteString
import com.kieronquinn.app.pcs.model.ClientGroupOverride
import com.kieronquinn.app.pcs.model.PcsClient
import com.kieronquinn.app.pcs.providers.ConfigProvider
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.DEBUG_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.PSI_CLIENT_GROUP_OVERRIDE_PROPERTY_NAME
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepositoryImpl.Companion.FLAG_REPOSITORY
import com.kieronquinn.app.pcs.utils.extensions.DeviceConfig_getString
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_get
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import com.kieronquinn.app.pcs.utils.extensions.buildId
import com.kieronquinn.app.pcs.utils.extensions.client
import com.kieronquinn.app.pcs.utils.extensions.clientGroup
import com.kieronquinn.app.pcs.utils.extensions.country
import com.kieronquinn.app.pcs.utils.extensions.device
import com.kieronquinn.app.pcs.utils.extensions.deviceModel
import com.kieronquinn.app.pcs.utils.extensions.deviceTier
import com.kieronquinn.app.pcs.utils.extensions.modelType
import com.kieronquinn.app.pcs.utils.extensions.toKeysetHandle
import com.kieronquinn.app.pcs.utils.extensions.variant
import com.kieronquinn.app.pcs.utils.extensions.version
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
class ProtectedDownloadGrpcService(
    private val context: Context
): ProtectedDownloadServiceGrpc.ProtectedDownloadServiceImplBase(), KoinComponent {
    
    companion object {
        private const val TAG = "AstreaService"
    }

    private val scope = MainScope()
    private val manifestRepository by inject<ManifestRepository>()

    override fun getManifestConfig(
        request: GetManifestConfigRequest,
        responseObserver: StreamObserver<GetManifestConfigResponse>
    ) {
        when (request.getType()) {
            RequestType.AICORE -> getManifestConfigAiCore(request, responseObserver)
            RequestType.PHONE -> getManifestConfigPhone(request, responseObserver)
            RequestType.TTS -> getManifestConfigTts(request, responseObserver)
            RequestType.AGENT -> getManifestConfigAgent(request, responseObserver)
            null -> {
                Log.e("AstreaServiceError", "Unknown request type")
                Log.e("AstreaServiceError", request.toString())
                responseObserver.onError(Throwable())
            }
        }
    }

    private fun getManifestConfigAiCore(
        request: GetManifestConfigRequest,
        responseObserver: StreamObserver<GetManifestConfigResponse>
    ) {
        if (SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)) {
            request.logAiCore()
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
            val clientGroupOverride = ClientGroupOverride.from(
                SystemProperties_get(PSI_CLIENT_GROUP_OVERRIDE_PROPERTY_NAME)
            ).clientGroup
            val manifest = manifestRepository.getManifest(url, request, clientGroupOverride)
            responseObserver.sendBackManifest(
                manifest,
                request.constraints.clientId,
                request.cryptoKeys
            )
        }
    }

    private fun getManifestConfigPhone(
        request: GetManifestConfigRequest,
        responseObserver: StreamObserver<GetManifestConfigResponse>
    ) {
        if (SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)) {
            request.logPhone()
        }
        scope.launch(Dispatchers.IO) {
            val url = ConfigProvider.getRepositoryUrl(context) ?: run {
                Log.e("AstreaServiceError", "No URL set, unable to download manifest")
                responseObserver.onError(Throwable())
                return@launch
            }
            val manifest = manifestRepository.getPhoneManifest(url, request.constraints.clientId)
            responseObserver.sendBackManifest(
                manifest,
                request.constraints.clientId,
                request.cryptoKeys
            )
        }
    }

    private fun getManifestConfigTts(
        request: GetManifestConfigRequest,
        responseObserver: StreamObserver<GetManifestConfigResponse>
    ) {
        if (SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)) {
            request.logTts()
        }
        scope.launch(Dispatchers.IO) {
            val url = ConfigProvider.getRepositoryUrl(context) ?: run {
                Log.e("AstreaServiceError", "No URL set, unable to download manifest")
                responseObserver.onError(Throwable())
                return@launch
            }
            val manifest = manifestRepository.getTtsManifest(
                url, request.constraints.modelType ?: request.constraints.clientId
            )
            responseObserver.sendBackManifest(
                manifest,
                request.constraints.clientId,
                request.cryptoKeys
            )
        }
    }

    private fun getManifestConfigAgent(
        request: GetManifestConfigRequest,
        responseObserver: StreamObserver<GetManifestConfigResponse>
    ) {
        if (SystemProperties_getBoolean(DEBUG_PROPERTY_NAME, false)) {
            request.logAgent()
        }
        scope.launch(Dispatchers.IO) {
            val url = ConfigProvider.getRepositoryUrl(context) ?: run {
                Log.e("AstreaServiceError", "No URL set, unable to download manifest")
                responseObserver.onError(Throwable())
                return@launch
            }
            val manifest = manifestRepository.getAgentManifest(
                url, request.constraints.clientId, request.constraints.device
            )
            responseObserver.sendBackManifest(
                manifest,
                request.constraints.clientId,
                request.cryptoKeys
            )
        }
    }

    private fun StreamObserver<GetManifestConfigResponse>.sendBackManifest(
        manifest: ByteArray?,
        clientId: String,
        cryptoKeys: CryptoKeys
    ) {
        if (manifest == null) {
            Log.e("AstreaServiceError", "No suitable manifest found for $clientId")
            onError(Throwable())
            return
        } else {
            Log.d(TAG, "Sending back manifest for $clientId")
        }
        val key = cryptoKeys.publicKey.toByteArray().toKeysetHandle()
        val encrypted = key.getPrimitive(
            RegistryConfiguration.get(),
            HybridEncrypt::class.java
        ).encrypt(manifest, clientId.toByteArray())
        val response = GetManifestConfigResponse.newBuilder()
            .setEncryptedManifestConfig(ByteString.copyFrom(encrypted))
            .setManifestTransformResult(ManifestTransformResult.newBuilder()
                .setCompressedManifest(false)
                .build()
            ).build()
        onNext(response)
        onCompleted()
    }

    private fun GetManifestConfigRequest.getType(): RequestType? {
        val constraintLabels = constraints.labelList.map { it.attribute }
        return when {
            constraints.clientId.startsWith("com.google.android.apps.pixel.agent") -> {
                RequestType.AGENT
            }
            constraintLabels.contains("device_tier") -> RequestType.AICORE
            constraintLabels.contains("country") -> RequestType.PHONE
            constraintLabels.contains("device_model") -> RequestType.TTS
            else -> null
        }
    }

    @Synchronized
    private fun GetManifestConfigRequest.logAiCore() {
        Log.d(TAG, "==== Get Manifest Config Request (AICore) ====")
        Log.d(TAG, "Device tier: ${constraints.deviceTier}")
        Log.d(TAG, "Client: ${constraints.client}")
        Log.d(TAG, "Client Group: ${constraints.clientGroup}")
        Log.d(TAG, "Client Version: ${constraints.clientVersion.version}")
        Log.d(TAG, "Variant: ${constraints.variant}")
        Log.d(TAG, "Build ID: ${constraints.buildId}")
        Log.d(TAG, "Compress: ${manifestTransform.compressManifest}")
        Log.d(TAG, "==== End Manifest Config Request ====")
    }

    @Synchronized
    private fun GetManifestConfigRequest.logPhone() {
        Log.d(TAG, "==== Get Manifest Config Request (Phone) ====")
        Log.d(TAG, "Client ID: ${constraints.clientId}")
        Log.d(TAG, "Client Version: ${constraints.clientVersion.version}")
        Log.d(TAG, "Country: ${constraints.country}")
        Log.d(TAG, "Version: ${constraints.version}")
        Log.d(TAG, "Compress: ${manifestTransform.compressManifest}")
        Log.d(TAG, "==== End Manifest Config Request ====")
    }

    @Synchronized
    private fun GetManifestConfigRequest.logTts() {
        Log.d(TAG, "==== Get Manifest Config Request (TTS) ====")
        Log.d(TAG, "Client ID: ${constraints.clientId}")
        Log.d(TAG, "Client Version: ${constraints.clientVersion.version}")
        Log.d(TAG, "Device Model: ${constraints.deviceModel}")
        Log.d(TAG, "Model Type: ${constraints.modelType}")
        Log.d(TAG, "Build ID: ${constraints.buildId}")
        Log.d(TAG, "Compress: ${manifestTransform.compressManifest}")
        Log.d(TAG, "==== End Manifest Config Request ====")
    }

    @Synchronized
    private fun GetManifestConfigRequest.logAgent() {
        Log.d(TAG, "==== Get Manifest Config Request (Agent) ====")
        Log.d(TAG, "Client ID: ${constraints.clientId}")
        Log.d(TAG, "Client Version: ${constraints.clientVersion.version}")
        Log.d(TAG, "Device: ${constraints.device}")
        Log.d(TAG, "Build ID: ${constraints.buildId}")
        Log.d(TAG, "Compress: ${manifestTransform.compressManifest}")
        Log.d(TAG, "==== End Manifest Config Request ====")
    }

    private enum class RequestType {
        AICORE, PHONE, TTS, AGENT
    }

}