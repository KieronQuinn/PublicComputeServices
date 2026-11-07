package com.kieronquinn.app.pcs.repositories

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 *  The root shell the voice pack repositories use to read Phone's files and install packs into
 *  Phone's data directory.
 */
internal class RootShell {

    private var _shell: Shell? = null
    private val shell: Shell
        get() = _shell ?: Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build().also {
            _shell = it
        }

    suspend fun isRootAvailable() = withContext(Dispatchers.IO) {
        shell.isRoot.also {
            // A shell opened before root was granted stays unprivileged: retry with a new one.
            if (!it) {
                _shell?.close()
                _shell = null
            }
        }
    }

    /** Runs [command]. A plain libsu job does not collect stdout, so ask for it. */
    fun run(command: String): Shell.Result =
        shell.newJob().add(command).to(ArrayList()).exec()

    /** Copies a file only root can read into [workDirectory] to read it. */
    fun read(path: String, workDirectory: File): ByteArray {
        val copy = File(workDirectory.apply { mkdirs() }, "read.tmp")
        copy.delete()
        val result = run("cat ${q(path)} > ${q(copy.path)} && chmod 644 ${q(copy.path)}")
        if (!result.isSuccess || !copy.isFile) error("Unable to read $path")
        return copy.readBytes().also { copy.delete() }
    }

    companion object {
        /** Quotes for sh; paths come from package info and fixed names, but stay safe regardless. */
        fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"
    }

}
