package com.leafyin.f50monitor.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * F50 本地管理页的 gofrm 接口。免登录可读，只靠 Referer 过 CSRF 校验。
 * GET http://<gateway>/gofrm/goform_get_cmd_process?multi_data=1&cmd=...
 *
 * 注意：这里刻意不用 https，也不用第三方 HTTP 客户端；
 * 明文访问由 networkSecurityConfig 放行，网络出口由 NetworkUtil 绑定到 Wi-Fi。
 */
object RouterApi {

    private val FIELDS = listOf(
        "network_type",
        "network_provider",
        "signalbar",
        "Z5g_rsrp",
        "ppp_status",
        "modem_main_state",
        "wan_ipaddr",
        "monthly_rx_bytes",
        "monthly_tx_bytes",
        "realtime_rx_thrpt",
        "realtime_tx_thrpt",
        "monthly_time",
        "wifi_onoff_state",
        "station_list",
        "web_version",
        "cr_version",
        "hardware_version",
    )

    data class Result(val snapshot: RouterSnapshot, val elapsedMs: Long)

    enum class Kind { TIMEOUT, NO_ROUTE, BAD_STATUS, BAD_PAYLOAD, UNKNOWN }

    class ApiException(val kind: Kind, message: String) : Exception(message)

    fun fetch(gateway: String, timeoutMs: Int = 4000): Result {
        val cmd = FIELDS.joinToString(",")
        val url = URL("http://$gateway/goform/goform_get_cmd_process?isTest=false&multi_data=1&cmd=" +
            URLEncoder.encode(cmd, "UTF-8"))
        val started = System.nanoTime()
        var conn: HttpURLConnection? = null

        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Referer", "http://$gateway/index.html")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val code = conn.responseCode
            if (code != 200) throw ApiException(Kind.BAD_STATUS, "设备返回 HTTP $code")

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw ApiException(Kind.BAD_PAYLOAD, "设备返回的数据无法解析")
            }
            return Result(parse(json), (System.nanoTime() - started) / 1_000_000)
        } catch (e: ApiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException(Kind.TIMEOUT, "连接超时")
        } catch (e: java.net.ConnectException) {
            throw ApiException(Kind.NO_ROUTE, "连不上 $gateway")
        } catch (e: Exception) {
            throw ApiException(Kind.UNKNOWN, e.message ?: "读取失败")
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(json: JSONObject): RouterSnapshot = RouterSnapshot(
        timestamp = System.currentTimeMillis(),
        networkType = str(json, "network_type"),
        provider = str(json, "network_provider"),
        signalBars = (num(json, "signalbar") ?: 0).toInt(),
        rsrp = num(json, "Z5g_rsrp")?.toInt(),
        pppStatus = str(json, "ppp_status"),
        wanIp = str(json, "wan_ipaddr"),
        monthlyRX = num(json, "monthly_rx_bytes") ?: 0,
        monthlyTX = num(json, "monthly_tx_bytes") ?: 0,
        realtimeRX = num(json, "realtime_rx_thrpt") ?: 0,
        realtimeTX = num(json, "realtime_tx_thrpt") ?: 0,
        counterSeconds = (num(json, "monthly_time") ?: 0).toInt(),
        ssid = str(json, "wifi_chip1_ssid1_ssid"),
        stations = stations(json.opt("station_list")),
    )

    /** 设备会把数字说成字符串、把空值说成空串，这层容错必须保留。 */
    private fun str(json: JSONObject, key: String): String {
        val v = json.opt(key) ?: return ""
        return when (v) {
            is String -> v
            is Number -> v.toString()
            else -> ""
        }
    }

    private fun num(json: JSONObject, key: String): Long? {
        val v = json.opt(key) ?: return null
        return when (v) {
            is Number -> v.toLong()
            is String -> v.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.toLong()
            else -> null
        }
    }

    /** station_list 可能是数组，也可能是内嵌 JSON 的字符串。 */
    private fun stations(raw: Any?): List<Station> {
        val arr: JSONArray = when (raw) {
            is JSONArray -> raw
            is String -> {
                if (raw.isEmpty()) return emptyList()
                try {
                    JSONArray(raw)
                } catch (e: Exception) {
                    return emptyList()
                }
            }
            else -> return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val s = Station(
                mac = o.optString("mac_addr"),
                hostname = o.optString("hostname"),
                ip = o.optString("ip_addr"),
            )
            if (s.mac.isEmpty() && s.ip.isEmpty()) null else s
        }
    }
}
