package com.samaliyan.nimbus.core

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the Aether core as a child process and streams its output line by line.
 * A process per connection keeps every run clean: the core keeps its choices in process-wide
 * state, so a second run in the same process could inherit the first one's.
 */
class CoreRunner(private val workDir: File, private val binaries: Collection<String>) {
    private var process: Process? = null
    @Volatile private var stopping = false

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(launch: CoreLaunch, onLine: (String) -> Unit, onExit: (code: Int, requested: Boolean) -> Unit) {
        workDir.mkdirs()
        killStray()
        stopping = false
        val pb = ProcessBuilder(launch.args)
            .directory(workDir)
            .redirectErrorStream(true)
        pb.environment().putAll(launch.env)
        val p = pb.start()
        process = p
        runCatching { p.outputStream.close() }

        val reader = thread(name = "core-output", isDaemon = true) {
            try {
                p.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
            } catch (_: Exception) {
                // The stream closes when the process ends.
            }
        }
        // The end of the process, not the end of its output, is what counts: a helper it started
        // could keep the output open after the core itself is gone.
        thread(name = "core-wait", isDaemon = true) {
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            // Give the reader a moment to deliver the last lines, which hold the error if any.
            runCatching { reader.join(1500) }
            onExit(code, stopping)
        }
    }

    /** Asks the core to end (SIGTERM), then forces it after a few seconds. */
    fun stop() {
        val p = process ?: return
        stopping = true
        p.destroy()
        if (!runCatching { p.waitFor(4, TimeUnit.SECONDS) }.getOrDefault(false)) {
            p.destroyForcibly()
            runCatching { p.waitFor(2, TimeUnit.SECONDS) }
        }
        process = null
        // A core forced to end leaves psiphon or lyrebird it started behind.
        killStray()
    }

    /**
     * A core left behind by a crash of the app would still hold the proxy port. Android only
     * shows this app its own processes in /proc, so anything found there running our binaries is ours.
     */
    fun killStray() {
        val procs = File("/proc").listFiles() ?: return
        var killed = false
        for (dir in procs) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid == android.os.Process.myPid()) continue
            val cmd = runCatching { File(dir, "cmdline").readText() }.getOrNull() ?: continue
            if (cmd.substringBefore('\u0000') in binaries) {
                runCatching { android.os.Process.killProcess(pid) }
                killed = true
            }
        }
        if (killed) Thread.sleep(400)
    }
}
