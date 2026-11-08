package com.kieronquinn.app.pcs.repositories

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.kieronquinn.app.pcs.voicepack.VoicePack
import com.kieronquinn.app.pcs.voicepack.VoicePackLayout
import com.kieronquinn.app.pcs.voicepack.VoicePackLayout.MDD_GROUPS
import com.kieronquinn.app.pcs.voicepack.VoicePackLayout.Marker
import com.kieronquinn.app.pcs.voicepack.VoicePackRewriter
import com.kieronquinn.app.pcs.voicepack.VoicePackSpeech
import com.kieronquinn.app.pcs.voicepack.VoicePackTranslation
import com.kieronquinn.app.pcs.voicepack.VoicePackTranslations
import com.kieronquinn.app.pcs.voicepack.WavAudio
import com.kieronquinn.app.pcs.repositories.VoicePackRepository.State
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 *  Builds a voice pack in the phone's language on the device. [VoicePackRepositoryImpl] builds a
 *  feature's pack ([VoicePack]) from Phone's English zip; [HoldForMeVoiceRepository] records Hold
 *  for Me's sentences.
 *
 *  The source is the English zip Phone's download manager (MDD) fetched for the feature's group;
 *  it is found by the size and SHA-1 the group records. Its text files are rewritten with the
 *  translation shipped in PCS, and every spoken sentence is recorded with the phone's
 *  text-to-speech engine. Nothing from Google is downloaded or shipped by PCS.
 */
interface VoicePackRepository {

    sealed class State {
        /** Not checked yet, or checking. */
        data object Checking : State()
        /** No translation is shipped for the phone's language. */
        data class Unsupported(val locale: Locale) : State()
        data object NoRoot : State()
        data class NoVoice(val locale: Locale) : State()
        /** Phone has not downloaded the feature's English zip yet. */
        data class NoSource(val locale: Locale) : State()
        data class NotBuilt(val locale: Locale, val outdated: Boolean) : State()
        data class Building(val locale: Locale, val done: Int, val total: Int) : State()
        data class Ready(val locale: Locale, val voice: String) : State()
        data class Failed(val locale: Locale, val message: String) : State()
    }

    val state: StateFlow<State>

    /** Re-reads what is on the device. */
    fun refresh()

    /** Builds the pack for every English zip of the feature Phone has. */
    fun build()

}

class VoicePackRepositoryImpl(
    private val context: Context,
    private val pack: VoicePack
) : VoicePackRepository {

    companion object {
        private const val TAG = "VoicePack"
        private val ENGLISH_TAGS = listOf("en-US", "en-GB").map { it.toByteArray() }
        private const val MDD_DOWNLOADS = "datadownload"
    }

    /** An English zip of Phone's for the feature, with the MDD group it was downloaded for. */
    private class Source(val group: VoicePackLayout.Group, val zip: String) {
        val id get() = group.sourceId
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var buildJob: Job? = null
    private val _state = MutableStateFlow<State>(State.Checking)
    override val state = _state.asStateFlow()

    private val root = RootShell()

    private val workDirectory get() = File(context.cacheDir, pack.assetDirectory)
    private val audioCache get() = File(context.filesDir, "${pack.assetDirectory}/audio")

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
                    buildAll()
                } catch (e: VoicePackSpeech.NoVoiceException) {
                    State.NoVoice(locale)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to build the ${pack.title} pack", e)
                    State.Failed(locale, e.message ?: e.javaClass.simpleName)
                } finally {
                    workDirectory.deleteRecursively()
                }
            }
        }
    }

    private suspend fun check(): State {
        val asset = VoicePackTranslations.forPhone(context, pack)
            ?: return State.Unsupported(VoicePackTranslations.phoneLocale())
        val locale = asset.translation.locale
        if (!isRootAvailable()) return State.NoRoot
        val phone = phoneInfo() ?: return State.NoSource(locale)
        val sources = findSources(phone)
        if (sources.isEmpty()) return State.NoSource(locale)
        val markers = sources.map { readMarker(phone.filesDir, locale, it.group) }
        if (markers.any { it == null }) return State.NotBuilt(locale, outdated = markers.any { it != null })
        if (markers.any { it!!.translation != asset.hash }) return State.NotBuilt(locale, outdated = true)
        return State.Ready(locale, markers.first()!!.voice)
    }

    private suspend fun buildAll(): State {
        val asset = VoicePackTranslations.forPhone(context, pack)
            ?: return State.Unsupported(VoicePackTranslations.phoneLocale())
        val translation = asset.translation
        val locale = translation.locale
        if (!isRootAvailable()) return State.NoRoot
        val phone = phoneInfo() ?: return State.NoSource(locale)
        val sources = findSources(phone)
        if (sources.isEmpty()) return State.NoSource(locale)
        workDirectory.deleteRecursively()
        workDirectory.mkdirs()
        val localeRoot = File(VoicePackLayout.packRoot(phone.filesDir, pack), locale.toLanguageTag()).path
        try {
            return buildAll(asset.hash, translation, phone, sources, localeRoot)
        } finally {
            // A failed build leaves no half-written pack behind.
            run("rm -rf ${q(localeRoot)}/.staging-*")
        }
    }

    private suspend fun buildAll(
        hash: String,
        translation: VoicePackTranslation,
        phone: PhoneInfo,
        sources: List<Source>,
        localeRoot: String
    ): State {
        val locale = translation.locale
        // Each zip is unpacked next to its final directory first; text comes before speech, so a
        // zip with prompts the translation does not cover fails before any speech is synthesised.
        val rewriter = VoicePackRewriter(translation)
        val staged = sources.associateWith { source ->
            val staging = "$localeRoot/.staging-${source.id}"
            unzip(source.zip, staging)
            val files = listFiles(staging)
            val output = File(workDirectory, "text/${source.id}").apply { mkdirs() }
            files.filter(rewriter::isRewritten).forEach { name ->
                val result = rewriter.rewrite(name, readRoot("$staging/$name"))
                if (result.untranslated.isNotEmpty()) {
                    error("${result.untranslated.size} sentences in $name have no translation, " +
                            "for example \"${result.untranslated.first()}\"")
                }
                File(output, name).apply { parentFile?.mkdirs() }.writeBytes(result.bytes)
            }
            val audio = files.filter { it.endsWith(".wav") }.map { it.removeSuffix(".wav") }
            val unknown = audio.filter { it !in translation.prompts && !VoicePack.keepsSourceRecording(it) }
            if (unknown.isNotEmpty()) {
                error("${unknown.size} recordings have no translation, for example ${unknown.first()}")
            }
            Staged(staging, files, output, audio.filter { it in translation.prompts })
        }

        val sentences = staged.values.flatMap { it.recordings }.map { translation.prompts.getValue(it) }.distinct()
        val voice: String
        val sentenceFiles = HashMap<String, File>()
        VoicePackSpeech.open(context, locale, translation.speechRate).use { speech ->
            voice = speech.voice.name
            val cache = File(audioCache, "${locale.toLanguageTag()}/${voice}_${translation.speechRate}")
                .apply { mkdirs() }
            sentences.forEachIndexed { index, sentence ->
                _state.value = State.Building(locale, index, sentences.size)
                val file = File(cache, sha1(sentence) + ".wav")
                if (!file.isFile) {
                    val raw = File(workDirectory, "tts.wav")
                    speech.synthesise(sentence, raw)
                    val pcm = WavAudio.resample(WavAudio.read(raw.readBytes()), pack.sampleRate)
                    val temporary = File(cache, file.name + ".tmp")
                    temporary.writeBytes(WavAudio.write(pcm))
                    if (!temporary.renameTo(file)) error("Unable to save a recording")
                    raw.delete()
                }
                sentenceFiles[sentence] = file
            }
        }
        _state.value = State.Building(locale, sentences.size, sentences.size)

        for ((source, stage) in staged) {
            val marker = Marker(source.group.version, source.group.checksum, locale.toLanguageTag(), hash, voice)
            install(phone, source, locale, stage, stage.recordings
                .associateWith { sentenceFiles.getValue(translation.prompts.getValue(it)) }, marker)
        }
        prunePacks(phone, sources.map { it.id }.toSet())
        pruneAudioCache(locale, voice, translation.speechRate, sentenceFiles.values.toSet())
        return check()
    }

    private class Staged(val directory: String, val files: List<String>, val text: File, val recordings: List<String>)

    /** Unpacks [zip] into a new [directory], owned by root until [install] hands it to Phone. */
    private fun unzip(zip: String, directory: String) {
        val result = run(
            "rm -rf ${q(directory)} && mkdir -p ${q(directory)} && " +
                "unzip -oq -d ${q(directory)} ${q(zip)} 2>&1 && echo ok"
        )
        if (!result.isSuccess || result.out.lastOrNull() != "ok") {
            error("Unable to unpack Phone's ${pack.title} zip: ${result.out.takeLast(3).joinToString(" ")}")
        }
    }

    /**
     *  Replaces the translated files in the unpacked zip, then swaps it in, so Phone never sees a
     *  half-written pack. Files are owned and labelled like the rest of Phone's data.
     */
    private suspend fun install(
        phone: PhoneInfo,
        source: Source,
        locale: Locale,
        stage: Staged,
        recordings: Map<String, File>,
        marker: Marker
    ) = withContext(Dispatchers.IO) {
        val packRoot = VoicePackLayout.packRoot(phone.filesDir, pack).path
        val target = VoicePackLayout.packDirectory(phone.filesDir, pack, locale, source.id).path
        val staging = stage.directory
        val markerFile = File(workDirectory, "marker-${source.id}").apply { writeText(marker.write()) }
        val script = buildString {
            append("set -e\n")
            stage.text.walkTopDown().filter { it.isFile }.forEach {
                append("cp ${q(it.path)} ${q("$staging/${it.relativeTo(stage.text).path}")}\n")
            }
            recordings.forEach { (path, file) ->
                append("cp ${q(file.path)} ${q("$staging/$path.wav")}\n")
            }
            append("cp ${q(markerFile.path)} ${q("$staging/${VoicePackLayout.MARKER}")}\n")
            append("chown -R ${phone.uid}:${phone.uid} ${q(packRoot)}\n")
            append("chmod -R u+rwX,go-rwx ${q(packRoot)}\n")
            // Same SELinux label as Phone's own files (toybox chcon has no --reference).
            append("chcon -R \"\$(stat -c %C ${q(phone.filesDir.path)})\" ${q(packRoot)}\n")
            append("rm -rf ${q(target)}\n")
            append("mv ${q(staging)} ${q(target)}\n")
            append("echo ok\n")
        }
        val scriptFile = File(workDirectory, "install-${source.id}.sh").apply { writeText(script) }
        val result = run("sh ${q(scriptFile.path)} 2>&1")
        if (!result.isSuccess || result.out.lastOrNull() != "ok") {
            error("Installing the pack failed: ${result.out.takeLast(3).joinToString(" ")}")
        }
        val installed = listFiles(target).toSet()
        val expected = stage.files.toSet() + VoicePackLayout.MARKER
        if (installed != expected) {
            error("Installed pack is incomplete (${installed.size} of ${expected.size} files)")
        }
    }

    /** Removes packs built from zips Phone no longer has, in every language. */
    private fun prunePacks(phone: PhoneInfo, keep: Set<String>) {
        val packRoot = VoicePackLayout.packRoot(phone.filesDir, pack).path
        val stale = run("for d in ${q(packRoot)}/*/*/; do echo \"\${d%/}\"; done").out
            .filter { it.startsWith("$packRoot/") && File(it).name !in keep && !File(it).name.startsWith("*") }
        if (stale.isEmpty()) return
        run("rm -rf " + stale.joinToString(" ") { q(it) })
        Log.i(TAG, "Removed ${stale.size} outdated ${pack.title} packs")
    }

    /**
     *  Phone's English zips for the feature: every group of [VoicePack.groupName] in Phone's MDD
     *  metadata whose file is on the device with the recorded size and SHA-1, and holds an English
     *  pack.
     */
    private suspend fun findSources(phone: PhoneInfo): List<Source> = withContext(Dispatchers.IO) {
        val groups = readGroups(phone).distinctBy { it.sourceId }
        if (groups.isEmpty()) Log.i(TAG, "Phone has no $MDD_GROUPS entry for ${pack.groupName}")
        val downloads = File(phone.filesDir, MDD_DOWNLOADS).path
        groups.mapNotNull { group ->
            val result = run("find ${q(downloads)} -type f -size ${group.size}c -exec sha1sum {} + 2>/dev/null")
            val zip = result.out.firstNotNullOfOrNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                parts.getOrNull(1)?.takeIf { parts[0].lowercase(Locale.ROOT) == group.checksum }
            } ?: run {
                Log.i(TAG, "Group ${group.sourceId}: no downloaded file of ${group.size} bytes with that SHA-1")
                return@mapNotNull null
            }
            // Only an untouched English pack is a valid source; a zip already rewritten by
            // another tool would be translated twice.
            val specs = readRoot(zip, pack.specs) ?: run {
                Log.i(TAG, "Group ${group.sourceId}: $zip has no ${pack.specs}")
                return@mapNotNull null
            }
            if (ENGLISH_TAGS.none { contains(specs, it) }) {
                Log.i(TAG, "Group ${group.sourceId}: $zip is not an English pack")
                return@mapNotNull null
            }
            Source(group, zip)
        }
    }

    private fun readGroups(phone: PhoneInfo): List<VoicePackLayout.Group> {
        val xml = try {
            readRoot("${phone.dataDir}/shared_prefs/$MDD_GROUPS")
        } catch (e: IllegalStateException) {
            Log.i(TAG, "Unable to read Phone's download groups: ${e.message}")
            return emptyList()
        }
        return VoicePackLayout.readMddGroups(pack, xml.toString(Charsets.UTF_8))
    }

    private fun readMarker(filesDir: File, locale: Locale, group: VoicePackLayout.Group): Marker? {
        val path = File(VoicePackLayout.packDirectory(filesDir, pack, locale, group.sourceId), VoicePackLayout.MARKER)
        val text = run("cat ${q(path.path)} 2>/dev/null").out.joinToString("\n")
        return text.takeIf { it.isNotBlank() }?.let(Marker::read)
            ?.takeIf { it.version == group.version && it.checksum == group.checksum }
    }

    private fun listFiles(directory: String): List<String> {
        return run("cd ${q(directory)} && find . -type f").out
            .filter { it.startsWith("./") }.map { it.removePrefix("./") }.sorted()
    }

    /** Copies a file Phone owns into PCS's cache to read it. */
    private fun readRoot(path: String): ByteArray = root.read(path, workDirectory)

    /** Copies one entry of a zip Phone owns into PCS's cache to read it; null if it is missing. */
    private fun readRoot(zip: String, entry: String): ByteArray? {
        val copy = File(workDirectory.apply { mkdirs() }, "read.tmp")
        copy.delete()
        val result = run("unzip -p ${q(zip)} ${q(entry)} > ${q(copy.path)} && chmod 644 ${q(copy.path)}")
        if (!result.isSuccess || !copy.isFile || copy.length() == 0L) return null.also { copy.delete() }
        return copy.readBytes().also { copy.delete() }
    }

    private fun pruneAudioCache(locale: Locale, voice: String, rate: Float, keep: Set<File>) {
        File(audioCache, locale.toLanguageTag()).listFiles().orEmpty().forEach { directory ->
            if (directory.name != "${voice}_$rate") directory.deleteRecursively()
            else directory.listFiles().orEmpty().filter { it !in keep }.forEach { it.delete() }
        }
    }

    private class PhoneInfo(val uid: Int, val dataDir: File) {
        val filesDir get() = File(dataDir, "files")
    }

    private fun phoneInfo(): PhoneInfo? = try {
        val info = context.packageManager.getApplicationInfo(VoicePackLayout.PHONE_PACKAGE, 0)
        PhoneInfo(info.uid, File(info.dataDir))
    } catch (e: PackageManager.NameNotFoundException) {
        Log.i(TAG, "Phone is not installed")
        null
    }

    private suspend fun isRootAvailable() = root.isRootAvailable()

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun run(command: String): Shell.Result = root.run(command)

    private fun q(value: String) = RootShell.q(value)

}
