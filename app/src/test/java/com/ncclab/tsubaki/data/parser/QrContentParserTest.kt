package com.ncclab.tsubaki.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [QrContentParser]. The parser must not depend on any
 * `android.*` class, so this test file deliberately stays Android-free and
 * uses plain JUnit 4 assertions.
 */
class QrContentParserTest {

    @Test
    fun wechatHttps_isWeChat() {
        val result = QrContentParser.parse("https://weixin.qq.com/r/abc")
        assertTrue(result is QrContent.WeChat)
        assertEquals(QrCategory.WECHAT, result.category)
    }

    @Test
    fun wechatScheme_isWeChat() {
        val result = QrContentParser.parse("weixin://dl/business/?ticket=abc")
        assertTrue(result is QrContent.WeChat)
    }

    @Test
    fun wxpScheme_isWeChat() {
        // WeChat Pay merchant code, common on printed receipts.
        val result = QrContentParser.parse("wxp://f2f0bSXJL3K1xRmZW8xX")
        assertTrue(result is QrContent.WeChat)
    }

    @Test
    fun schemelessWeChatHost_isWeChat() {
        val result = QrContentParser.parse("weixin.qq.com/r/abcdef")
        assertTrue(result is QrContent.WeChat)
    }

    @Test
    fun qqHttps_isQq() {
        val result = QrContentParser.parse("https://qm.qq.com/cgi-bin/qm/qr?k=xyz")
        assertTrue(result is QrContent.Qq)
        assertEquals(QrCategory.QQ, result.category)
    }

    @Test
    fun mqqApiScheme_isQq() {
        val result = QrContentParser.parse("mqqapi://card/show_pslcard?src_type=internal&uin=10000")
        assertTrue(result is QrContent.Qq)
    }

    @Test
    fun alipayHttps_isAlipay() {
        val result = QrContentParser.parse("https://qr.alipay.com/abc123")
        assertTrue(result is QrContent.Alipay)
        assertEquals(QrCategory.ALIPAY, result.category)
    }

    @Test
    fun alipayScheme_isAlipay() {
        val result = QrContentParser.parse("alipays://platformapi/startapp?appId=10000007")
        assertTrue(result is QrContent.Alipay)
    }

    @Test
    fun schemelessAlipayHost_isAlipay() {
        val result = QrContentParser.parse("qr.alipay.com/abc123")
        assertTrue(result is QrContent.Alipay)
    }

    @Test
    fun wifi_happyPath() {
        val raw = "WIFI:T:WPA;S:HomeNet;P:hunter2;H:false;;"
        val parsed = QrContentParser.parse(raw)
        assertTrue(parsed is QrContent.Wifi)
        val wifi = parsed as QrContent.Wifi
        assertEquals("HomeNet", wifi.ssid)
        assertEquals("hunter2", wifi.password)
        assertEquals("WPA", wifi.encryption)
        assertFalse(wifi.hidden)
    }

    @Test
    fun wifi_escapedSemicolonInSsid() {
        val raw = "WIFI:S:Free Wi\\;fi;T:nopass;;"
        val parsed = QrContentParser.parse(raw)
        assertTrue(parsed is QrContent.Wifi)
        val wifi = parsed as QrContent.Wifi
        assertEquals("Free Wi;fi", wifi.ssid)
        assertEquals("nopass", wifi.encryption)
        assertNull(wifi.password)
    }

    @Test
    fun mailto_withQuery() {
        val raw = "mailto:foo@bar.com?subject=Hi&body=Hello"
        val parsed = QrContentParser.parse(raw)
        assertTrue(parsed is QrContent.Email)
        val email = parsed as QrContent.Email
        assertEquals("foo@bar.com", email.address)
        assertEquals("Hi", email.subject)
        assertEquals("Hello", email.body)
    }

    @Test
    fun mailto_plain() {
        val parsed = QrContentParser.parse("mailto:foo@bar.com")
        assertTrue(parsed is QrContent.Email)
        val email = parsed as QrContent.Email
        assertEquals("foo@bar.com", email.address)
        assertNull(email.subject)
        assertNull(email.body)
    }

    @Test
    fun tel_isPhone() {
        val parsed = QrContentParser.parse("tel:+861234")
        assertTrue(parsed is QrContent.Phone)
        assertEquals("+861234", (parsed as QrContent.Phone).number)
    }

    @Test
    fun smsto_withBody() {
        val parsed = QrContentParser.parse("SMSTO:10086:hello")
        assertTrue(parsed is QrContent.Sms)
        val sms = parsed as QrContent.Sms
        assertEquals("10086", sms.number)
        assertEquals("hello", sms.body)
    }

    @Test
    fun sms_withoutBody() {
        val parsed = QrContentParser.parse("sms:10086")
        assertTrue(parsed is QrContent.Sms)
        val sms = parsed as QrContent.Sms
        assertEquals("10086", sms.number)
        assertNull(sms.body)
    }

    @Test
    fun geo_withQuery() {
        val parsed = QrContentParser.parse("geo:30.0,120.0?q=Hangzhou")
        assertTrue(parsed is QrContent.Geo)
        val geo = parsed as QrContent.Geo
        assertEquals(30.0, geo.lat, 0.0001)
        assertEquals(120.0, geo.lng, 0.0001)
        assertEquals("Hangzhou", geo.query)
    }

    @Test
    fun mecard_isContact() {
        val parsed = QrContentParser.parse("MECARD:N:Doe;TEL:1234;;")
        assertTrue(parsed is QrContent.Contact)
        assertEquals(QrCategory.CONTACT, parsed.category)
    }

    @Test
    fun vcard_isContact() {
        val raw = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nEND:VCARD"
        val parsed = QrContentParser.parse(raw)
        assertTrue(parsed is QrContent.Contact)
    }

    @Test
    fun plainHttps_isUrl() {
        val parsed = QrContentParser.parse("https://example.com")
        assertTrue(parsed is QrContent.Url)
        assertEquals("https://example.com", (parsed as QrContent.Url).url)
    }

    @Test
    fun plainText_fallsThrough() {
        val parsed = QrContentParser.parse("hello world")
        assertTrue(parsed is QrContent.Text)
        assertEquals("hello world", parsed.rawValue)
    }

    @Test
    fun bareHostname_isText() {
        // No scheme, so we deliberately classify as Text rather than guessing.
        val parsed = QrContentParser.parse("example.com")
        assertTrue(parsed is QrContent.Text)
    }
}
