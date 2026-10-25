package hev.htproxy

/**
 * JNI entry points of hev-socks5-tunnel, which carries the VPN's packets to the local SOCKS5
 * proxy. The native library registers these by this exact package and class name.
 */
object TProxyService {
    @Volatile private var loaded = false

    fun load(): Boolean {
        if (!loaded) loaded = runCatching { System.loadLibrary("hev-socks5-tunnel") }.isSuccess
        return loaded
    }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
    @JvmStatic external fun TProxyStopService(): Boolean
    @JvmStatic external fun TProxyIsRunning(): Boolean
    /** [tx packets, tx bytes, rx packets, rx bytes] */
    @JvmStatic external fun TProxyGetStats(): LongArray?
}
