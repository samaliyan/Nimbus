package com.samaliyan.nimbus.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface TunnelState {
    data object Idle : TunnelState
    data class Starting(val stage: ConnectStage) : TunnelState
    data class Connected(val since: Long, val peer: String?) : TunnelState
    /** [stalled]: the core reports the tunnel up, but a test request through it got no answer. */
    data class Reconnecting(val since: Long, val peer: String?, val stalled: Boolean = false) : TunnelState
    data object Stopping : TunnelState
    data class Failed(val message: String) : TunnelState
}

val TunnelState.isActive: Boolean
    get() = this is TunnelState.Starting || this is TunnelState.Connected || this is TunnelState.Reconnecting

data class Traffic(val up: Long = 0, val down: Long = 0, val upRate: Long = 0, val downRate: Long = 0)

data class ExitInfo(val ip: String, val country: String, val colo: String, val warp: String)

/** Shared, process-wide view of the connection, written by the service and read by the UI. */
object Tunnel {
    private const val MAX_LOG = 2000

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    private val _exit = MutableStateFlow<ExitInfo?>(null)
    val exit: StateFlow<ExitInfo?> = _exit.asStateFlow()

    /** Round trip of a small HTTPS request through the tunnel, in ms; null = not measured. */
    private val _latency = MutableStateFlow<Long?>(null)
    val latency: StateFlow<Long?> = _latency.asStateFlow()

    /** The settings the running connection was started with. */
    @Volatile var startedWith: Settings? = null

    fun setState(s: TunnelState) { _state.value = s }

    fun updateState(f: (TunnelState) -> TunnelState) = _state.update(f)

    fun log(line: LogLine) {
        _logs.update { old ->
            if (old.size >= MAX_LOG) old.subList(old.size - MAX_LOG + 200, old.size) + line else old + line
        }
    }

    fun note(message: String, level: Level = Level.INFO) {
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        log(LogLine(now, level, "[app] $message"))
    }

    fun clearLogs() { _logs.value = emptyList() }

    fun setTraffic(t: Traffic) { _traffic.value = t }
    fun setExit(e: ExitInfo?) { _exit.value = e }
    fun setLatency(ms: Long?) { _latency.value = ms }

    /** Grows by one per connection; a result from an older connection is dropped. */
    @Volatile var session = 0
        private set

    fun resetSession() {
        session++
        _traffic.value = Traffic()
        _exit.value = null
        _latency.value = null
    }
}
