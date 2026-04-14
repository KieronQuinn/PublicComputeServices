package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT_TTS
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.TTS_ENABLED
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean

object TtsHooks: GrpcHooks() {

    override val tag = "TtsHooks"
    override val applicationClassName =
        "com.google.android.apps.speech.tts.googletts.GoogleTTSRoot_Application"
    override val serviceClassName =
        "com.google.android.libraries.speech.transcription.recognition.grpc.GoogleAsrService"
    override val port = PORT_TTS

    override fun isEnabled(): Boolean {
        return SystemProperties_getBoolean(TTS_ENABLED, false)
    }

}