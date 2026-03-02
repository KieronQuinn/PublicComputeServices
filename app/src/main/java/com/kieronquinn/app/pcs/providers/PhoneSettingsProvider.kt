package com.kieronquinn.app.pcs.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.repositories.SettingsRepositoryImpl
import com.kieronquinn.app.pcs.utils.extensions.callSafely
import kotlin.system.exitProcess

/**
 *  Allows access to the locally stored shared prefs which contains settings and flag data for
 *  Phone
 */
class PhoneSettingsProvider: ContentProvider() {

    companion object {
        private const val METHOD_GET = "get"
        private const val EXTRA_SETTINGS = "settings"

        private val URI_PHONE_SETTINGS = "content://${BuildConfig.APPLICATION_ID}.settings.phone".toUri()

        private val PACKAGE_ALLOWLIST = setOf(
            PACKAGE_NAME_PHONE
        )

        fun getSettings(context: Context): PhoneSettings? {
            return try {
                context.contentResolver
                    .callSafely(URI_PHONE_SETTINGS, METHOD_GET, null, null)
                    ?.apply { classLoader = PhoneSettings::class.java.classLoader }
                    ?.getParcelable(EXTRA_SETTINGS, PhoneSettings::class.java)
            }catch (e: Exception) {
                null
            }
        }
    }

    // DI is disabled for this process, so create a repository instance for just this class
    private val settings by lazy {
        SettingsRepositoryImpl(requireContext())
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!PACKAGE_ALLOWLIST.contains(callingPackage)) {
            return null
        }
        return when(method) {
            METHOD_GET -> bundleOf(EXTRA_SETTINGS to getSettings())
            else -> null
        }.also {
            killAfterDelay()
        }
    }

    private fun getSettings() = PhoneSettings(
        settings.phoneSharpieEnabled.getSync(),
        settings.phoneDobbyEnabled.getSync(),
        settings.phoneDobbyUrl.getSync().takeIf { it.isNotBlank() },
        settings.phoneDobbyDuplexFiles.getSync(),
        settings.phoneDobbyRegion.getSync(),
        settings.phoneAtlasEnabled.getSync(),
        settings.phoneAtlasModels.getSync(),
        settings.phoneBeeslyEnabled.getSync(),
        settings.phoneBeesly.getSync().takeIf { it.isNotBlank() },
        settings.phoneBeeslyRegion.getSync(),
        settings.phoneNautilusEnabled.getSync(),
        settings.phoneSonicEnabled.getSync(),
        settings.phoneXatuEnabled.getSync(),
        settings.phoneXatuModels.getSync(),
        settings.phoneCallerTagsEnabled.getSync(),
        settings.phoneFermatEnabled.getSync(),
        settings.phoneExpressoEnabled.getSync(),
        settings.phonePatrickPhase.getSync(),
        settings.phoneCallRecordingEnabled.getSync()
    )

    private fun killAfterDelay() = Thread {
        Thread.sleep(500L)
        exitProcess(0)
    }.start()

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

}