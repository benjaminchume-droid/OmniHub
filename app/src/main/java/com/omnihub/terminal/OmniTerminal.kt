package com.omnihub.terminal

import android.content.Context
import com.omnihub.policy.PermissionEngine
import com.omnihub.policy.RiskLevel
import com.omnihub.workspace.OmniWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class OmniTerminal(
    private val context: Context,
    private val workspace: OmniWorkspace
) {
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val shell: String
    )

    enum class Shell { SH, BASH, TOYBOX }

    suspend fun run(
        command: String,
        shell: Shell = Shell.SH,
        timeoutSec: Long = 60,
        workDir: File? = null
    ): Result = withContext(Dispatchers.IO) {
        val risk = classify(command)
        if (!PermissionEngine.allow(risk)) {
            return@withContext Result(-1, "", "Blocked by permission engine (risk=$risk)", shell.name)
        }
        val cwd = workDir ?: workspace.root()
        val binary = when (shell) {
            Shell.BASH -> if (File("/system/bin/bash").exists()) "/system/bin/bash" else "/system/bin/sh"
            Shell.TOYBOX -> "/system/bin/toybox"
            Shell.SH -> "/system/bin/sh"
        }
        val pb = ProcessBuilder(binary, "-c", command)
            .directory(cwd)
            .redirectErrorStream(false)
        val env = pb.environment()
        env["HOME"] = cwd.absolutePath
        env["OMNI_HOME"] = cwd.absolutePath
        val proc = pb.start()
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@withContext Result(-1, "", "Timed out after ${timeoutSec}s", shell.name)
        }
        val out = proc.inputStream.bufferedReader().readText()
        val err = proc.errorStream.bufferedReader().readText()
        Result(proc.exitValue(), out, err, shell.name)
    }

    suspend fun git(args: String): Result =
        run("git $args", workDir = workspace.root())

    private fun classify(command: String): RiskLevel {
        val c = command.lowercase()
        return when {
            c.contains("rm -rf /") || c.contains("mkfs") || c.contains(":(){") -> RiskLevel.CRITICAL
            c.contains("curl ") || c.contains("wget ") || c.contains("git push") || c.contains("git clone") -> RiskLevel.EXTERNAL
            c.contains("rm ") || c.contains("mv ") || c.contains("chmod ") -> RiskLevel.DESTRUCTIVE
            c.contains("git ") || c.contains("mkdir ") || c.contains("touch ") -> RiskLevel.LOCAL_WRITE
            else -> RiskLevel.READ
        }
    }
}
