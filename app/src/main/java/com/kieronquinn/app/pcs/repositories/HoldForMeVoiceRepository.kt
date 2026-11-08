package com.kieronquinn.app.pcs.repositories

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.kieronquinn.app.pcs.repositories.RootShell.Companion.q
import com.kieronquinn.app.pcs.repositories.VoicePackRepository.State
import com.kieronquinn.app.pcs.voicepack.HoldForMePrompts
import com.kieronquinn.app.pcs.voicepack.VoicePackLayout
import com.kieronquinn.app.pcs.voicepack.VoicePackSpeech
import com.kieronquinn.app.pcs.voicepack.VoicePackTranslations
import com.kieronquinn.app.pcs.voicepack.WavAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 *  Records Hold for Me's sentences to the other party ([HoldForMePrompts]) in the phone's
 *  language with the phone's text-to-speech voice, and installs them in Phone's data directory.
 *  The translation is shipped in `assets/holdforme/`; nothing from Google is used.
 */
class HoldForMeVoiceRepository(private val context: Context) : VoicePackRepository {

    companion object {
        private const val TAG = "HoldForMeVoice"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val root = RootShell()
    private var buildJob: Job? = null
    private val _state = MutableStateFlow<State>(State.Checking)
    override val state = _state.asStateFlow()

    private val workDirectory get() = File(context.cacheDir, HoldForMePrompts.ASSET_DIRECTORY)

    override fun refresh() {
        if (buildJob?.isActive == true) return
        scope.launch {
            mutex.withLock { _state.value = check() }
        }
    }

    override fun build() {
        if (buildJob?.isActive == true) return
        buildJob = scope.launch {
            mutex.withLock {
                val locale = VoicePackTranslations.phoneLocale()
                _state.value = try {
                    buildPrompts()
                } catch (e: VoicePackSpeech.NoVoiceException) {
                    State.NoVoice(locale)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to record the Hold for Me sentences", e)
                    State.Failed(locale, e.message ?: e.javaClass.simpleName)
                } finally {
                    workDirectory.deleteRecursively()
                }
            }
        }
    }

    private suspend fun check(): State {
        val asset = VoicePackTranslations.forPhone(context, HoldForMePrompts.ASSET_DIRECTORY)
            ?: return State.Unsupported(VoicePackTranslations.phoneLocale())
        val locale = asset.translation.locale
        if (!root.isRootAvailable()) return State.NoRoot
        val filesDir = phoneFilesDir() ?: return State.NoSource(locale)
        val directory = HoldForMePrompts.packDirectory(filesDir, locale)
        val marker = root.run("cat ${q(File(directory, HoldForMePrompts.MARKER).path)} 2>/dev/null").out
            .joinToString("\n").takeIf { it.isNotBlank() }?.let(HoldForMePrompts.Marker::read)
            ?: return State.NotBuilt(locale, outdated = false)
        val complete = HoldForMePrompts.PROMPTS.all {
            root.run("test -s ${q(HoldForMePrompts.recording(directory, it).path)}").isSuccess
        }
        if (!complete || marker.translation != asset.hash || marker.locale != locale.toLanguageTag()) {
            return State.NotBuilt(locale, outdated = true)
        }
        return State.Ready(locale, marker.voice)
    }

    private suspend fun buildPrompts(): State {
        val asset = VoicePackTranslations.forPhone(context, HoldForMePrompts.ASSET_DIRECTORY)
            ?: return State.Unsupported(VoicePackTranslations.phoneLocale())
        val translation = asset.translation
        val locale = translation.locale
        val missing = HoldForMePrompts.PROMPTS.filter { it !in translation.prompts }
        if (missing.isNotEmpty()) error("The translation has no sentence for ${missing.joinToString()}")
        if (!root.isRootAvailable()) return State.NoRoot
        val (uid, filesDir) = phoneInfo() ?: return State.NoSource(locale)
        workDirectory.deleteRecursively()
        val recordings = File(workDirectory, "recordings").apply { mkdirs() }
        val voice = VoicePackSpeech.open(context, locale, translation.speechRate).use { speech ->
            HoldForMePrompts.PROMPTS.forEachIndexed { index, prompt ->
                _state.value = State.Building(locale, index, HoldForMePrompts.PROMPTS.size)
                val raw = File(workDirectory, "tts.wav")
                speech.synthesise(translation.prompts.getValue(prompt), raw)
                val pcm = WavAudio.resample(WavAudio.read(raw.readBytes()), HoldForMePrompts.SAMPLE_RATE)
                HoldForMePrompts.recording(recordings, prompt).writeBytes(WavAudio.write(pcm))
                raw.delete()
            }
            speech.voice.name
        }
        _state.value = State.Building(locale, HoldForMePrompts.PROMPTS.size, HoldForMePrompts.PROMPTS.size)
        File(recordings, HoldForMePrompts.MARKER)
            .writeText(HoldForMePrompts.Marker(locale.toLanguageTag(), asset.hash, voice).write())
        install(uid, filesDir, HoldForMePrompts.packDirectory(filesDir, locale), recordings)
        return check()
    }

    /**
     *  Copies the recordings next to their final directory, then swaps it in, so Phone never sees
     *  a half-written set. Files are owned and labelled like the rest of Phone's data. Other
     *  languages are removed: only the phone's language is played.
     */
    private fun install(uid: Int, filesDir: File, target: File, recordings: File) {
        val packRoot = HoldForMePrompts.packRoot(filesDir).path
        val staging = "$packRoot/.staging"
        val script = buildString {
            append("set -e\n")
            append("rm -rf ${q(staging)}\n")
            append("mkdir -p ${q(staging)}\n")
            recordings.listFiles().orEmpty().forEach {
                append("cp ${q(it.path)} ${q("$staging/${it.name}")}\n")
            }
            append("chown -R $uid:$uid ${q(packRoot)}\n")
            append("chmod -R u+rwX,go-rwx ${q(packRoot)}\n")
            // Same SELinux label as Phone's own files (toybox chcon has no --reference).
            append("chcon -R \"\$(stat -c %C ${q(filesDir.path)})\" ${q(packRoot)}\n")
            append("for d in ${q(packRoot)}/*/; do rm -rf \"\$d\"; done\n")
            append("mv ${q(staging)} ${q(target.path)}\n")
            append("echo ok\n")
        }
        val scriptFile = File(workDirectory, "install.sh").apply { writeText(script) }
        val result = root.run("sh ${q(scriptFile.path)} 2>&1")
        if (!result.isSuccess || result.out.lastOrNull() != "ok") {
            root.run("rm -rf ${q(staging)}")
            error("Installing the sentences failed: ${result.out.takeLast(3).joinToString(" ")}")
        }
    }

    private fun phoneInfo(): Pair<Int, File>? = try {
        val info = context.packageManager.getApplicationInfo(VoicePackLayout.PHONE_PACKAGE, 0)
        info.uid to File(info.dataDir, "files")
    } catch (e: PackageManager.NameNotFoundException) {
        Log.i(TAG, "Phone is not installed")
        null
    }

    private fun phoneFilesDir() = phoneInfo()?.second

}
