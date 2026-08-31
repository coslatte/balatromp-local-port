package com.balatromp.nativebridge.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuShell {

    const val PERMISSION_REQUEST_CODE = 1001

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun requestPermission() {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    @Suppress("DiscouragedPrivateApi")
    private fun newProcess(cmd: Array<String>): Process {
        // Shizuku.newProcess is private since 13.1.2, use reflection (deprecated but still works)
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(null, cmd, null, null) as Process
    }

    fun runCommand(vararg cmd: String): Process = newProcess(arrayOf(*cmd))

    fun runShell(command: String): Process = newProcess(arrayOf("sh", "-c", command))

    fun runShellOk(command: String): Boolean = try {
        val p = runShell(command)
        p.waitFor()
        p.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
