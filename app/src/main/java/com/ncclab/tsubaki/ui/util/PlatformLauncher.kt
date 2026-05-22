package com.ncclab.tsubaki.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Helpers for opening WeChat / QQ / Alipay specific URIs.
 *
 * The launch flow is "try the packaged intent, fall back to no package, then
 * Toast" so the UI never crashes when the target app is missing.
 */
object PlatformLauncher {

    const val PKG_WECHAT = "com.tencent.mm"
    const val PKG_QQ = "com.tencent.mobileqq"
    const val PKG_ALIPAY = "com.eg.android.AlipayGphone"

    fun launchInWeChat(context: Context, uri: String) {
        launchWithPackage(context, uri, PKG_WECHAT, "微信")
    }

    fun launchInQq(context: Context, uri: String) {
        launchWithPackage(context, uri, PKG_QQ, "QQ")
    }

    fun launchInAlipay(context: Context, uri: String) {
        launchWithPackage(context, uri, PKG_ALIPAY, "支付宝")
    }

    /**
     * Try to open [uri] inside [pkg]. If the package is not installed or
     * cannot handle the intent, fall back to letting the OS resolve the
     * intent. If even that fails, show a Toast naming the missing app.
     */
    fun launchWithPackage(context: Context, uri: String, pkg: String, appName: String) {
        val parsed = try {
            Uri.parse(uri)
        } catch (_: Exception) {
            Toast.makeText(context, "无法解析链接", Toast.LENGTH_SHORT).show()
            return
        }
        val packaged = Intent(Intent.ACTION_VIEW, parsed).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(packaged)
            return
        } catch (_: ActivityNotFoundException) {
            // fall through to OS-resolved intent
        }
        val fallback = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallback)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未安装$appName", Toast.LENGTH_SHORT).show()
        }
    }

    fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
