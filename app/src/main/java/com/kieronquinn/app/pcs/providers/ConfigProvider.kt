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
import com.kieronquinn.app.pcs.model.PcsClient.BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES
import com.kieronquinn.app.pcs.repositories.PhenotypeRepositoryImpl.Companion.FLAG_REPOSITORY
import com.kieronquinn.app.pcs.utils.extensions.callSafely
import com.topjohnwu.superuser.Shell
import kotlin.system.exitProcess

/**
 *  Not all apps have Device Config permission so can't read the repository URL. However, those
 *  that don't have the permission will instead be able to interact with Public Compute Services,
 *  so they can call this provider and get the URL via root instead.
 */
class ConfigProvider: ContentProvider() {

    companion object {
        private const val METHOD_GET = "get"
        private const val EXTRA_REPOSITORY_URL = "repository_url"

        private val URI_CONFIG = "content://${BuildConfig.APPLICATION_ID}.config".toUri()

        private val PACKAGE_ALLOWLIST = setOf(
            PACKAGE_NAME_PHONE
        )

        fun getRepositoryUrl(context: Context): String? {
            return try {
                context.contentResolver
                    .callSafely(URI_CONFIG, METHOD_GET, null, null)
                    ?.getString(EXTRA_REPOSITORY_URL, null)
            }catch (e: Exception) {
                null
            }
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!PACKAGE_ALLOWLIST.contains(callingPackage)) {
            return null
        }
        return when(method) {
            METHOD_GET -> bundleOf(EXTRA_REPOSITORY_URL to getRepositoryUrl())
            else -> null
        }.also {
            killAfterDelay()
        }
    }

    private fun getRepositoryUrl(): String? {
        val out = ArrayList<String>()
        val result = Shell.getShell()
            .newJob()
            .to(out, out)
            .add("device_config get ${DEVICE_PERSONALIZATION_SERVICES.value} $FLAG_REPOSITORY")
            .exec()
        return if (result.isSuccess) {
            out.first()
        } else null
    }

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