package com.ncclab.tsubaki.data.parser

import java.net.URI
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Pure-JVM parser that classifies a raw QR string into a [QrContent] variant.
 *
 * Detection is best-effort and ordered: the first matching branch wins.
 * The parser intentionally avoids any `android.*` API so it can be unit
 * tested on the JVM (see QrContentParserTest).
 */
object QrContentParser {

    // WeChat hosts and schemes
    private val WECHAT_HOSTS = listOf(
        "weixin.qq.com",
        "u.wechat.com",
        "w.url.cn",
        "wx.tenpay.com",
    )
    // `wxp://` is the WeChat Pay merchant code scheme commonly seen on
    // printed receipts (`wxp://f2f0…`). It is owned by `com.tencent.mm`
    // just like `weixin://`, so we treat it as a WeChat payload.
    private val WECHAT_SCHEMES = listOf("weixin", "wxp")

    // QQ hosts and schemes
    private val QQ_HOSTS = listOf(
        "qm.qq.com",
        "qr.qq.com",
        "connect.qq.com",
    )
    private val QQ_SCHEMES = listOf("mqqapi", "mqq", "tencent")

    // Alipay hosts and schemes
    private val ALIPAY_HOSTS = listOf(
        "qr.alipay.com",
        "render.alipay.com",
        "mobilecodec.alipay.com",
    )
    private val ALIPAY_SCHEMES = listOf("alipays", "alipay")

    /**
     * Conservative URL pattern. Matches `http://` or `https://` followed by
     * at least one non-whitespace character. `ftp://` is intentionally not
     * recognised because the manifest does not declare an `ftp` VIEW intent
     * and most phones lack an FTP handler, so a "Open in browser" button on
     * an `ftp://` payload would just toast "无法打开". Bare hostnames without
     * a scheme deliberately fall through to [QrContent.Text] so that
     * ambiguous strings like "example.com" stay as text.
     */
    private val URL_PATTERN: Pattern = Pattern.compile(
        "^(?i)https?://\\S+$"
    )

    fun parse(raw: String): QrContent {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return QrContent.Text(raw)

        val lower = trimmed.lowercase()

        // (a) WeChat
        if (matchesPlatform(trimmed, lower, WECHAT_SCHEMES, WECHAT_HOSTS)) {
            return QrContent.WeChat(trimmed)
        }
        // (b) QQ
        if (matchesPlatform(trimmed, lower, QQ_SCHEMES, QQ_HOSTS)) {
            return QrContent.Qq(trimmed)
        }
        // (c) Alipay
        if (matchesPlatform(trimmed, lower, ALIPAY_SCHEMES, ALIPAY_HOSTS)) {
            return QrContent.Alipay(trimmed)
        }
        // (d) Wifi
        if (lower.startsWith("wifi:")) {
            parseWifi(trimmed)?.let { return it }
        }
        // (e) Email
        if (lower.startsWith("mailto:")) {
            return parseMailto(trimmed)
        }
        if (trimmed.startsWith("MATMSG:")) {
            return parseMatMsg(trimmed)
        }
        // (f) Phone
        if (lower.startsWith("tel:")) {
            val number = trimmed.substring("tel:".length)
            return QrContent.Phone(number = number, rawValue = trimmed)
        }
        // (g) SMS
        if (lower.startsWith("smsto:") || lower.startsWith("sms:")) {
            return parseSms(trimmed)
        }
        // (h) Geo
        if (lower.startsWith("geo:")) {
            parseGeo(trimmed)?.let { return it }
        }
        // (i) Contact (MECARD / vCard)
        if (trimmed.startsWith("MECARD:", ignoreCase = true) ||
            trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)
        ) {
            return QrContent.Contact(trimmed)
        }
        // (j) URL
        if (URL_PATTERN.matcher(trimmed).matches()) {
            return QrContent.Url(url = trimmed, rawValue = trimmed)
        }
        // (k) Fallback
        return QrContent.Text(raw)
    }

    private fun matchesPlatform(
        raw: String,
        lower: String,
        schemes: List<String>,
        hosts: List<String>,
    ): Boolean {
        for (scheme in schemes) {
            if (lower.startsWith("$scheme://")) return true
        }
        // Inspect host for both schemed URIs (`https://qr.alipay.com/...`)
        // and scheme-less hostnames (`qr.alipay.com/...`) commonly seen on
        // printed materials. Suffix matching uses a leading `.` so spoofed
        // hosts like `qm.qq.com.attacker.com` do not match.
        val host = (safeHost(raw) ?: schemelessHost(raw))?.lowercase()
        if (host != null) {
            for (h in hosts) {
                if (host == h || host.endsWith(".$h")) return true
            }
        }
        return false
    }

    private fun safeHost(raw: String): String? {
        return try {
            val uri = URI.create(raw)
            uri.host
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Best-effort host extraction for inputs that have no `scheme://` prefix
     * (e.g. `qr.alipay.com/abc` or `weixin.qq.com/r/xyz`). We only return
     * something that looks like a hostname (contains a `.`, no whitespace,
     * not just a path) so that arbitrary text like "hello world" still
     * falls through to [QrContent.Text].
     */
    private fun schemelessHost(raw: String): String? {
        if (raw.contains("://")) return null
        if (raw.any { it.isWhitespace() }) return null
        val end = raw.indexOfAny(charArrayOf('/', '?', '#'))
        val candidate = if (end < 0) raw else raw.substring(0, end)
        if (candidate.isEmpty() || !candidate.contains('.')) return null
        // Reject obvious non-hostname tokens (a hostname has no ':' before
        // a possible port, but for our purposes we only care about platform
        // host suffix matching, which is alphabetic).
        if (candidate.contains(':')) return null
        return candidate
    }

    /**
     * Parse the standard `WIFI:T:<auth>;S:<ssid>;P:<password>;H:<bool>;;`
     * envelope. Supports backslash escaping inside values: `\;`, `\,`,
     * `\:`, `\\` and `\"` are unescaped.
     */
    private fun parseWifi(raw: String): QrContent.Wifi? {
        val body = raw.substring("WIFI:".length)
        val fields = splitWifiFields(body)
        var ssid: String? = null
        var password: String? = null
        var encryption: String? = null
        var hidden = false
        for (field in fields) {
            val colon = field.indexOf(':')
            if (colon <= 0) continue
            val key = field.substring(0, colon).uppercase()
            val value = unescapeWifi(field.substring(colon + 1))
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> encryption = value
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }
        val finalSsid = ssid ?: return null
        return QrContent.Wifi(
            ssid = finalSsid,
            password = password?.takeIf { it.isNotEmpty() },
            encryption = encryption?.takeIf { it.isNotEmpty() },
            hidden = hidden,
            rawValue = raw,
        )
    }

    /** Walks the body character-by-character and splits on unescaped `;`. */
    private fun splitWifiFields(body: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                // Keep the escape and the next char so unescapeWifi can process them.
                current.append(c)
                current.append(body[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                if (current.isNotEmpty()) {
                    fields.add(current.toString())
                    current.setLength(0)
                }
                i += 1
                continue
            }
            current.append(c)
            i += 1
        }
        if (current.isNotEmpty()) fields.add(current.toString())
        return fields
    }

    private fun unescapeWifi(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                out.append(value[i + 1])
                i += 2
            } else {
                out.append(c)
                i += 1
            }
        }
        return out.toString()
    }

    private fun parseMailto(raw: String): QrContent.Email {
        val body = raw.substring("mailto:".length)
        val q = body.indexOf('?')
        val address: String
        var subject: String? = null
        var body2: String? = null
        if (q < 0) {
            address = body
        } else {
            address = body.substring(0, q)
            val query = body.substring(q + 1)
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                val name = if (eq >= 0) pair.substring(0, eq) else pair
                val value = if (eq >= 0) pair.substring(eq + 1) else ""
                val decoded = try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {
                    value
                }
                when (name.lowercase()) {
                    "subject" -> subject = decoded
                    "body" -> body2 = decoded
                }
            }
        }
        return QrContent.Email(
            address = address,
            subject = subject,
            body = body2,
            rawValue = raw,
        )
    }

    /**
     * MATMSG:TO:foo@bar.com;SUB:Hi;BODY:Hello;;
     * Lightweight handling: extract TO/SUB/BODY fields with the same WIFI-style splitter.
     */
    private fun parseMatMsg(raw: String): QrContent.Email {
        val body = raw.substring("MATMSG:".length)
        val fields = splitWifiFields(body)
        var to = ""
        var subject: String? = null
        var msgBody: String? = null
        for (field in fields) {
            val colon = field.indexOf(':')
            if (colon <= 0) continue
            val key = field.substring(0, colon).uppercase()
            val value = unescapeWifi(field.substring(colon + 1))
            when (key) {
                "TO" -> to = value
                "SUB" -> subject = value
                "BODY" -> msgBody = value
            }
        }
        return QrContent.Email(
            address = to,
            subject = subject,
            body = msgBody,
            rawValue = raw,
        )
    }

    private fun parseSms(raw: String): QrContent.Sms {
        val lower = raw.lowercase()
        val prefixLen = when {
            lower.startsWith("smsto:") -> "smsto:".length
            lower.startsWith("sms:") -> "sms:".length
            else -> 0
        }
        val body = raw.substring(prefixLen)
        val colon = body.indexOf(':')
        return if (colon < 0) {
            QrContent.Sms(number = body, body = null, rawValue = raw)
        } else {
            val number = body.substring(0, colon)
            val msg = body.substring(colon + 1).takeIf { it.isNotEmpty() }
            QrContent.Sms(number = number, body = msg, rawValue = raw)
        }
    }

    private fun parseGeo(raw: String): QrContent.Geo? {
        val body = raw.substring("geo:".length)
        val q = body.indexOf('?')
        val coords = if (q < 0) body else body.substring(0, q)
        val parts = coords.split(',')
        if (parts.size < 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        var query: String? = null
        if (q >= 0) {
            val rest = body.substring(q + 1)
            for (pair in rest.split('&')) {
                val eq = pair.indexOf('=')
                if (eq < 0) continue
                val name = pair.substring(0, eq)
                val value = pair.substring(eq + 1)
                if (name.equals("q", ignoreCase = true)) {
                    query = try {
                        URLDecoder.decode(value, "UTF-8")
                    } catch (_: Exception) {
                        value
                    }
                }
            }
        }
        return QrContent.Geo(lat = lat, lng = lng, query = query, rawValue = raw)
    }
}
