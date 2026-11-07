package com.kieronquinn.app.pcs.translation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.kieronquinn.app.pcs.BuildConfig
import com.kieronquinn.app.pcs.PcsApplication.Companion.PACKAGE_NAME_PHONE

/**
 *  Gives hooked apps [PhoneLanguageTranslator]: they cannot bundle ML Kit, and the model is
 *  downloaded once, here.
 *
 *  `from_english`: extras `lines` (String[]) to `status` ([PhoneLanguageTranslator.Status] name)
 *  and, when OK, `lines` of the same length. When the model is missing its download is queued and
 *  `MODEL_NOT_READY` returned at once; the caller does not wait for it.
 */
class TranslationProvider : ContentProvider() {

    companion object {
        private const val TAG = "TranslationProvider"
        private const val METHOD_FROM_ENGLISH = "from_english"
        private const val EXTRA_LINES = "lines"
        private const val EXTRA_STATUS = "status"

        private val URI = "content://${BuildConfig.APPLICATION_ID}.translation".toUri()

        private val PACKAGE_ALLOWLIST = setOf(PACKAGE_NAME_PHONE)

        /**
         *  For hooks: [lines] translated into the phone's language where they are English, or null
         *  (logged with [log]) if that cannot be done now. Must not be called on the main thread.
         */
        fun fromEnglish(context: Context, lines: List<String>, log: (String) -> Unit): List<String>? {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                log("Translation skipped on the main thread")
                return null
            }
            val result = try {
                context.contentResolver.call(URI, METHOD_FROM_ENGLISH, null,
                    bundleOf(EXTRA_LINES to lines.toTypedArray()))
            } catch (e: Exception) {
                log("Translation unavailable: $e")
                return null
            }
            val status = result?.getString(EXTRA_STATUS)
            val translated = result?.getStringArray(EXTRA_LINES)
            if (status != PhoneLanguageTranslator.Status.OK.name || translated == null || translated.size != lines.size) {
                log("Translation not done: ${status ?: "no answer"}")
                return null
            }
            return translated.toList()
        }
    }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (callingPackage !in PACKAGE_ALLOWLIST) {
            Log.w(TAG, "Refused uid ${Binder.getCallingUid()}")
            return null
        }
        if (method != METHOD_FROM_ENGLISH) return null
        val lines = extras?.getStringArray(EXTRA_LINES)?.toList()
            ?: return bundleOf(EXTRA_STATUS to PhoneLanguageTranslator.Status.ERROR.name)
        val token = Binder.clearCallingIdentity()
        try {
            val result = PhoneLanguageTranslator.fromEnglish(lines)
            if (result.status == PhoneLanguageTranslator.Status.MODEL_NOT_READY) {
                TranslationModelWorker.enqueue(requireContext())
            }
            return bundleOf(
                EXTRA_STATUS to result.status.name,
                EXTRA_LINES to result.lines?.toTypedArray()
            )
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

}
