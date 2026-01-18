package com.kieronquinn.app.pcs.repositories

import android.util.Base64
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.DeviceConfigEntry
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  Uses libsu to get/override/clear DeviceConfig and SystemProperties entries. Due to the backend
 *  using `Settings.Config`, it's not possible to use libsu's RootService (which would be much
 *  faster), so we use batched calls to `device_config` instead to read/write config, and to write
 *  properties. Reading properties is not restricted and uses the regular hidden method.
 */
interface DeviceConfigPropertiesRepository {

    companion object {
        const val DEBUG_PROPERTY_NAME = "persist.pcs.debug"
        const val PHONE_FLAGS_PROPERTY_NAME = "persist.pcs.phone_flags"
    }

    /**
     *  Returns whether root access is available
     */
    suspend fun isAvailable(): Boolean

    /**
     *  Gets device config entries for a given namespace. Since the command output dumps everything
     *  as strings, only string values are returned.
     */
    suspend fun getConfig(namespace: String): List<DeviceConfigEntry>

    /**
     *  Overrides a list of device config entries. This is batched into a single Shell input for
     *  optimisation.
     */
    suspend fun overrideConfig(entries: List<DeviceConfigEntry>)

    /**
     *  Clears all device config entries. The [DeviceConfigEntry.value] field is not used here.
     *  This is batched into a single Shell input for optimisation.
     */
    suspend fun clearConfigOverrides(entries: List<DeviceConfigEntry>)

    /**
     *  Sets a system property as root to a given value
     */
    suspend fun setProperty(name: String, value: String)

    data class DeviceConfigEntry(
        val namespace: String,
        val flag: String,
        val value: String? = null
    ) {

        fun getBytes(): ByteArray {
            return Base64.decode(value, Base64.NO_WRAP)
        }

        fun getLong(): Long? {
            return value?.toLongOrNull()
        }

    }

}

class DeviceConfigPropertiesRepositoryImpl : DeviceConfigPropertiesRepository {

    companion object {
        private const val LIST = "device_config list"
        private const val OVERRIDE = "device_config override"
        private const val CLEAR = "device_config clear_override"
        private const val SPLIT = "="
    }

    private var _shell: Shell? = null

    private val shell
        get() = _shell ?: run {
            Shell.Builder.create().build().also {
                _shell = it
            }
        }

    override suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            ArrayList<String>().also {
                shell.newJob().add("whoami").to(it).exec()
            }.firstOrNull() == "root"
        }.also {
            if(!it) {
                // If we don't have root, always clear the shell for the next retry
                _shell?.close()
                _shell = null
            }
        }
    }

    override suspend fun getConfig(namespace: String): List<DeviceConfigEntry> {
        return withContext(Dispatchers.IO) {
            ArrayList<String>().also {
                shell.newJob().add("$LIST $namespace").to(it).exec()
            }.parseNamespaceOutput(namespace)
        }
    }

    override suspend fun overrideConfig(entries: List<DeviceConfigEntry>) {
        withContext(Dispatchers.IO) {
            shell.newJob().apply {
                entries.forEach {
                    add("$OVERRIDE ${it.namespace} ${it.flag} ${it.value}")
                }
            }.exec()
        }
    }

    override suspend fun clearConfigOverrides(entries: List<DeviceConfigEntry>) {
        withContext(Dispatchers.IO) {
            shell.newJob().apply {
                entries.forEach {
                    add("$CLEAR ${it.namespace} ${it.flag}")
                }
            }.exec()
        }
    }

    override suspend fun setProperty(name: String, value: String) {
        withContext(Dispatchers.IO) {
            shell.newJob().add("setprop $name $value").exec()
        }
    }

    private fun ArrayList<String>.parseNamespaceOutput(namespace: String): List<DeviceConfigEntry> {
        return mapNotNull {
            if(!it.contains(SPLIT)) return@mapNotNull null
            it.split(SPLIT).let { pair ->
                DeviceConfigEntry(namespace, pair[0], pair[1])
            }
        }
    }

}