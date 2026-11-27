package com.leafyin.f50monitor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * 选网络 + 取网关。Android 上和 macOS 最大的差别：
 * 手机连着 F50 的 Wi-Fi（无互联网）时，默认路由可能仍走蜂窝，
 * 请求 http://192.168.0.1 会从移动数据出去然后失败。
 * 所以必须显式挑一个带 Wi-Fi 传输的 Network，并把进程绑上去。
 */
object NetworkUtil {

    const val FALLBACK_GATEWAY = "192.168.0.1"

    data class Endpoint(val network: Network?, val gateway: String, val localIp: String?)

    @Suppress("DEPRECATION")
    fun resolve(context: Context, override: String? = null): Endpoint {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val wifi = cm.allNetworks.firstOrNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        val chosen = wifi ?: cm.activeNetwork
        val auto = gatewayOf(cm.getLinkProperties(chosen)) ?: FALLBACK_GATEWAY
        val gateway = override?.takeIf { it.isNotBlank() } ?: auto
        val localIp = localIpOf(cm.getLinkProperties(chosen))
        return Endpoint(chosen, gateway, localIp)
    }

    /** 绑定后，本进程新建的 socket 都从这个网络出去。 */
    fun bind(cm: ConnectivityManager, network: Network?) {
        cm.bindProcessToNetwork(network)
    }

    fun gatewayOf(lp: LinkProperties?): String? {
        if (lp == null) return null
        // 优先默认路由的网关；Wi-Fi 场景下就是路由器
        val route = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
        return route?.gateway?.hostAddress
    }

    fun localIpOf(lp: LinkProperties?): String? {
        if (lp == null) return null
        return lp.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull()
            ?.hostAddress
    }
}
