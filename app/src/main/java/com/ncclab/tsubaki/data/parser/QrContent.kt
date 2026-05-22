package com.ncclab.tsubaki.data.parser

/**
 * High level category for a scanned QR string. Used to drive UI affordances
 * (chip label, primary action button, icon) on the result screen.
 */
enum class QrCategory {
    URL,
    WECHAT,
    QQ,
    ALIPAY,
    WIFI,
    EMAIL,
    PHONE,
    SMS,
    GEO,
    CONTACT,
    TEXT,
}

/**
 * A parsed representation of a QR code payload. Each variant carries the
 * typed fields required by the UI plus the original [rawValue] so the
 * result screen can still show / copy / share what the camera saw.
 */
sealed class QrContent {
    abstract val rawValue: String
    abstract val category: QrCategory

    data class Url(val url: String, override val rawValue: String = url) : QrContent() {
        override val category: QrCategory = QrCategory.URL
    }

    data class WeChat(override val rawValue: String) : QrContent() {
        override val category: QrCategory = QrCategory.WECHAT
    }

    data class Qq(override val rawValue: String) : QrContent() {
        override val category: QrCategory = QrCategory.QQ
    }

    data class Alipay(override val rawValue: String) : QrContent() {
        override val category: QrCategory = QrCategory.ALIPAY
    }

    data class Wifi(
        val ssid: String,
        val password: String?,
        val encryption: String?,
        val hidden: Boolean,
        override val rawValue: String,
    ) : QrContent() {
        override val category: QrCategory = QrCategory.WIFI
    }

    data class Email(
        val address: String,
        val subject: String?,
        val body: String?,
        override val rawValue: String,
    ) : QrContent() {
        override val category: QrCategory = QrCategory.EMAIL
    }

    data class Phone(val number: String, override val rawValue: String) : QrContent() {
        override val category: QrCategory = QrCategory.PHONE
    }

    data class Sms(
        val number: String,
        val body: String?,
        override val rawValue: String,
    ) : QrContent() {
        override val category: QrCategory = QrCategory.SMS
    }

    data class Geo(
        val lat: Double,
        val lng: Double,
        val query: String?,
        override val rawValue: String,
    ) : QrContent() {
        override val category: QrCategory = QrCategory.GEO
    }

    data class Contact(override val rawValue: String) : QrContent() {
        override val category: QrCategory = QrCategory.CONTACT
    }

    data class Text(override val rawValue: String) : QrContent() {
        override val category: QrCategory = QrCategory.TEXT
    }
}
