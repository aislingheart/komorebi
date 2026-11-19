/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Raycast Extensions
 * File: CliBridge.kt
 * Description: Sandboxed shell bridge for extensions that use child_process.
 */

package com.sameerasw.essentials.raycast

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit

data class CliResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

object CliBridge {

    fun execute(
        context: Context,
        supportDir: File,
        extRoot: File,
        command: String,
        cwd: String? = null,
        timeoutMs: Long = 15_000,
    ): CliResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return CliResult("", "No command provided", 1)
        }

        val workingDir = resolveWorkingDir(cwd, supportDir, extRoot) ?: supportDir.apply { mkdirs() }
        val firstToken = trimmed.substringBefore(' ').trim().trim('"', '\'')
        val firstArg = trimmed.substringAfter(' ', "").trim().trim('"', '\'')

        when (firstToken) {
            "open", "xdg-open" -> {
                if (firstArg.startsWith("http://") || firstArg.startsWith("https://")) {
                    return runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(firstArg)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                        CliResult("", "", 0)
                    }.getOrElse { CliResult("", it.message ?: "Unable to open URL", 1) }
                }
                return CliResult("", "Opening local files through the CLI bridge is not supported.", 1)
            }

            "pbpaste" -> {
                val text = runCatching {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = cm?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    } else {
                        ""
                    }
                }.getOrDefault("")
                return CliResult(text, "", 0)
            }
        }

        val process = runCatching {
            ProcessBuilder("/system/bin/sh", "-c", trimmed)
                .directory(workingDir)
                .apply {
                    environment()["HOME"] = supportDir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/product/bin"
                }
                .start()
        }.getOrElse {
            return CliResult("", it.message ?: "Unable to start shell", 1)
        }

        val maxOutputChars = 512 * 1024
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (stdout.length < maxOutputChars) {
                        stdout.appendLine(line.take(maxOutputChars - stdout.length))
                    }
                }
            }
        }
        val errThread = Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { line ->
                    if (stderr.length < maxOutputChars) {
                        stderr.appendLine(line.take(maxOutputChars - stderr.length))
                    }
                }
            }
        }
        outThread.start()
        errThread.start()

        val finished = process.waitFor(timeoutMs.coerceIn(1_000, 60_000), TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            outThread.join(500)
            errThread.join(500)
            return CliResult(
                stdout.toString(),
                "Command timed out after ${timeoutMs.coerceIn(1_000, 60_000)} ms",
                124,
            )
        }

        outThread.join(1_000)
        errThread.join(1_000)
        val exitCode = runCatching { process.exitValue() }.getOrDefault(1)
        val errText = stderr.toString()
        return CliResult(
            stdout = stdout.toString(),
            stderr = if (exitCode == 127) {
                "$errText\nKomorebi's CLI bridge ran the command in an Android shell. Desktop binaries such as brew, docker, tmux or speedtest are not available on Android."
            } else {
                errText
            },
            exitCode = exitCode,
        )
    }

    private fun resolveWorkingDir(cwd: String?, supportDir: File, extRoot: File): File? {
        if (cwd.isNullOrBlank()) return supportDir.apply { mkdirs() }
        val candidate = runCatching { File(cwd).canonicalFile }.getOrNull() ?: return null
        val supportRoot = runCatching { supportDir.canonicalFile }.getOrNull() ?: return null
        val root = runCatching { extRoot.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf {
            it.isDirectory && (it.path == supportRoot.path || it.path.startsWith(supportRoot.path + File.separator) ||
                it.path == root.path || it.path.startsWith(root.path + File.separator))
        }
    }
}
