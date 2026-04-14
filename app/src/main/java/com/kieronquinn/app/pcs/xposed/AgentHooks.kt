package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT_AGENT
import com.kieronquinn.app.pcs.repositories.DeviceConfigPropertiesRepository.Companion.AGENT_ENABLED
import com.kieronquinn.app.pcs.utils.extensions.SystemProperties_getBoolean
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Member

object AgentHooks: GrpcHooks() {

    override val tag = "AgentHooks"
    override val applicationClassName =
        "com.google.android.apps.pixel.agent.Agent_Application"
    override val serviceClassName =
        "com.google.android.apps.pixel.agent.model.endpoints.ModelDownloadForegroundService"
    override val port = PORT_AGENT

    override fun isEnabled(): Boolean {
        return SystemProperties_getBoolean(AGENT_ENABLED, false)
    }

    override fun XC_LoadPackage.LoadPackageParam.getOdsConstructor(dexKit: DexKitBridge): Member? {
        val hostLoader = dexKit.findClass {
            matcher { usingStrings("Could not find a NameResolverProvider for %s%s") }
        }.singleOrNull()?.let {
            try {
                XposedHelpers.findClass(it.name, classLoader)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                null
            }
        } ?: run {
            return null
        }
        return hostLoader.constructors.firstOrNull {
            it.parameterTypes[0] == String::class.java
        }
    }

}