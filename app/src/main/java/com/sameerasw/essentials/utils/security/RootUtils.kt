/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Security Utilities
 * File: RootUtils.kt
 * Description: Helper methods for executing root shell actions and verifying superuser access.
 */

package com.sameerasw.essentials.utils

import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object RootUtils {
    private const val ROOT_CHECK_TIMEOUT_MS = 5_000L
    private const val ROOT_GRANT_TIMEOUT_MS = 10_000L
    private const val COMMAND_TIMEOUT_MS = 15_000L

    private val streamDrainExecutor =
        Executors.newCachedThreadPool { r ->
            Thread(r, "root-stream-drain").apply { isDaemon = true }
        }

    /**
     * Checks if the su binary is available on the device.
     */
    fun isRootAvailable(): Boolean = runAndWaitWithTimeout(arrayOf("sh", "-c", "which su"), ROOT_CHECK_TIMEOUT_MS)

    /**
     * Checks if root permission is granted by attempting to run 'id' via su.
     */
    fun isRootPermissionGranted(): Boolean = runAndWaitWithTimeout(arrayOf("su", "-c", "id"), ROOT_GRANT_TIMEOUT_MS)

    /**
     * Executes a shell command with root privileges.
     */
    fun runCommand(command: String): Boolean {
        var process: Process? = null
        var stdoutDrain: java.util.concurrent.Future<*>? = null
        var stderrDrain: java.util.concurrent.Future<*>? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val proc = process
            stdoutDrain =
                streamDrainExecutor.submit {
                    runCatching { proc.inputStream.use { it.drainFully() } }
                }
            stderrDrain =
                streamDrainExecutor.submit {
                    runCatching { proc.errorStream.use { it.drainFully() } }
                }
            proc.outputStream.use { os ->
                os.write("$command\n".toByteArray(Charsets.UTF_8))
                os.write("exit\n".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val completed = proc.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            completed && proc.exitValue() == 0
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        } finally {
            runCatching { process?.destroyForcibly() }
            stdoutDrain?.cancel(true)
            stderrDrain?.cancel(true)
        }
    }

    /**
     * Safely POSIX single-quotes a shell argument for use inside `su -c`.
     */
    internal fun shellQuote(arg: String): String = "'${arg.replace("'", "'\\''")}'"

    /**
     * Starts a new process with root privileges.
     */
    fun newProcess(cmd: Array<String>): Process? =
        try {
            val quotedCommand = cmd.joinToString(" ") { shellQuote(it) }
            Runtime.getRuntime().exec(arrayOf("su", "-c", quotedCommand))
        } catch (_: Exception) {
            null
        }

    private fun runAndWaitWithTimeout(
        cmd: Array<String>,
        timeoutMs: Long,
    ): Boolean {
        var process: Process? = null
        var stdoutDrain: java.util.concurrent.Future<*>? = null
        var stderrDrain: java.util.concurrent.Future<*>? = null
        return try {
            process = Runtime.getRuntime().exec(cmd)
            val proc = process
            stdoutDrain =
                streamDrainExecutor.submit {
                    runCatching { proc.inputStream.use { it.drainFully() } }
                }
            stderrDrain =
                streamDrainExecutor.submit {
                    runCatching { proc.errorStream.use { it.drainFully() } }
                }
            val completed = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            completed && proc.exitValue() == 0
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        } finally {
            runCatching { process?.destroyForcibly() }
            stdoutDrain?.cancel(true)
            stderrDrain?.cancel(true)
        }
    }

    private fun InputStream.drainFully() {
        val buf = ByteArray(8192)
        while (read(buf) >= 0) {
            // discard
        }
    }
}
