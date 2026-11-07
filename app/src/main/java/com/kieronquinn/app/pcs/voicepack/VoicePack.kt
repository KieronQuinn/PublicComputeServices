package com.kieronquinn.app.pcs.voicepack

/**
 *  A Phone feature whose spoken pack PCS can build in the phone's language. Each one is a zip
 *  Phone's download manager (MDD) fetches for a file group, with the same layout: protobuf text
 *  files and one recording per prompt.
 */
enum class VoicePack(
    /** Name used in logs. */
    val title: String,
    /** MDD file group Phone downloads the English zip for. */
    val groupName: String,
    /** The zip's intent specs, holding every prompt. */
    val specs: String,
    /** Directory under Phone's `files/` holding the localised packs. */
    val packDirectory: String,
    /** Directory under PCS's assets holding the translations, also used for PCS's own caches. */
    val assetDirectory: String,
    /** Sample rate of the pack's own speech recordings, used for the new ones. */
    val sampleRate: Int
) {
    CALL_SCREEN(
        title = "Call Screen",
        groupName = "dobby-duplex-files",
        specs = "agenticcallscreen_robot_intent_specs.pb",
        packDirectory = "pcs-call-screen",
        assetDirectory = "callscreen",
        sampleRate = 32_000
    ),
    TAKE_A_MESSAGE(
        title = "Take a Message",
        groupName = "beesly-duplex-files",
        specs = "voicemail_robot_intent_specs.pb",
        packDirectory = "pcs-take-a-message",
        assetDirectory = "takeamessage",
        sampleRate = 24_000
    );

    /** Phone unzips the group's file into `files/<group name>/<version>/`. */
    val sourceDirectory get() = groupName

    companion object {
        /** Keypad tones (`srdtmf…`) and silences (`…/silence-…`) carry no speech. */
        fun keepsSourceRecording(path: String): Boolean =
            path.startsWith("srdtmf") || path.substringAfterLast('/').startsWith("silence-")
    }
}
