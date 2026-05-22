package com.ncclab.tsubaki.data.wifi

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.ncclab.tsubaki.data.parser.QrContent

/**
 * 连接 Wi-Fi 的推荐方式：
 *   - Android 11 (API 30) 及以上：ACTION_WIFI_ADD_NETWORKS，系统会弹出原生确认面板
 *   - Android 10 (API 29)：addNetworkSuggestions，加入到建议列表
 *   - Android 9 及以下：跳到 Wi-Fi 设置页让用户手动连接
 */
object WifiConnector {

    sealed class Result {
        data object Launched : Result()
        data class Suggested(val message: String) : Result()
        data class FellBackToSettings(val message: String) : Result()
        data class Failed(val message: String) : Result()
    }

    fun connect(context: Context, wifi: QrContent.Wifi): Result {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> connectViaAddNetworks(context, wifi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> connectViaSuggestions(context, wifi)
            else -> openWifiSettings(context, "请在 Wi-Fi 设置中手动连接 ${wifi.ssid}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun connectViaAddNetworks(context: Context, wifi: QrContent.Wifi): Result {
        return try {
            val suggestion = buildSuggestion(wifi)
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                putParcelableArrayListExtra(
                    Settings.EXTRA_WIFI_NETWORK_LIST,
                    arrayListOf(suggestion)
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.Launched
        } catch (e: Exception) {
            openWifiSettings(context, "无法启动连接面板：${e.message ?: "未知错误"}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectViaSuggestions(context: Context, wifi: QrContent.Wifi): Result {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val suggestion = buildSuggestion(wifi)
            // 先移除可能存在的旧建议，避免 STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
            wifiManager.removeNetworkSuggestions(listOf(suggestion))
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Result.Suggested("已添加到 Wi-Fi 建议，请在通知栏点击连接 ${wifi.ssid}")
            } else {
                openWifiSettings(context, "添加 Wi-Fi 建议失败 (code=$status)，请手动连接")
            }
        } catch (e: Exception) {
            openWifiSettings(context, "添加建议异常：${e.message ?: "未知错误"}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildSuggestion(wifi: QrContent.Wifi): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(wifi.ssid)
            .setIsHiddenSsid(wifi.hidden)

        val pwd = wifi.password.orEmpty()
        when (normalizeEncryption(wifi.encryption)) {
            EncryptionMode.WPA3 -> if (pwd.isNotEmpty()) builder.setWpa3Passphrase(pwd)
            EncryptionMode.WPA -> if (pwd.isNotEmpty()) builder.setWpa2Passphrase(pwd)
            EncryptionMode.WEP -> {
                // WEP 在新版 API 已不再支持，按 WPA2 兜底（不一定能连上，由系统决定）
                if (pwd.isNotEmpty()) builder.setWpa2Passphrase(pwd)
            }
            EncryptionMode.NONE -> { /* open network */ }
        }
        return builder.build()
    }

    private enum class EncryptionMode { WPA3, WPA, WEP, NONE }

    private fun normalizeEncryption(token: String?): EncryptionMode {
        return when (token?.trim()?.uppercase()) {
            "WPA3", "SAE" -> EncryptionMode.WPA3
            "WPA", "WPA2", "WPA/WPA2", "WPA-PSK", "WPA2-PSK", "WPA-EAP", "WPA2-EAP" -> EncryptionMode.WPA
            "WEP" -> EncryptionMode.WEP
            "NOPASS", "NONE", null, "" -> EncryptionMode.NONE
            else -> EncryptionMode.WPA
        }
    }

    private fun openWifiSettings(context: Context, message: String): Result {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.FellBackToSettings(message)
        } catch (e: Exception) {
            Result.Failed("无法打开 Wi-Fi 设置：${e.message ?: "未知错误"}")
        }
    }
}
