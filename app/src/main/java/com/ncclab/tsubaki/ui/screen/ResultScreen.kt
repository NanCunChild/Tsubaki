package com.ncclab.tsubaki.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ncclab.tsubaki.data.parser.QrCategory
import com.ncclab.tsubaki.data.parser.QrContent
import com.ncclab.tsubaki.data.parser.QrContentParser
import com.ncclab.tsubaki.data.wifi.WifiConnector
import com.ncclab.tsubaki.ui.util.PlatformLauncher

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    format: String,
    rawValue: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val parsed = remember(rawValue) { QrContentParser.parse(rawValue) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Only check the package manager for the platform that this QR actually
    // belongs to. The result drives button visibility so the user does not
    // see "使用微信打开" on a phone without WeChat installed and only find
    // out after tapping.
    val platformInstalled = remember(parsed) {
        when (parsed) {
            is QrContent.WeChat -> PlatformLauncher.isPackageInstalled(context, PlatformLauncher.PKG_WECHAT)
            is QrContent.Qq -> PlatformLauncher.isPackageInstalled(context, PlatformLauncher.PKG_QQ)
            is QrContent.Alipay -> PlatformLauncher.isPackageInstalled(context, PlatformLauncher.PKG_ALIPAY)
            else -> false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("扫描结果") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        ResultBody(
            paddingValues = paddingValues,
            format = format,
            rawValue = rawValue,
            parsed = parsed,
            platformInstalled = platformInstalled,
            onCopy = {
                clipboardManager.setText(AnnotatedString(rawValue))
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
            onCopyText = { value, message ->
                clipboardManager.setText(AnnotatedString(value))
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onShare = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, rawValue)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            },
            onOpenInBrowser = { url ->
                runIntent(context, Intent(Intent.ACTION_VIEW, url.toUri()))
            },
            onLaunchInWeChat = { uri -> PlatformLauncher.launchInWeChat(context, uri) },
            onLaunchInQq = { uri -> PlatformLauncher.launchInQq(context, uri) },
            onLaunchInAlipay = { uri -> PlatformLauncher.launchInAlipay(context, uri) },
            onPlatformMissing = { appName ->
                Toast.makeText(context, "未安装$appName", Toast.LENGTH_SHORT).show()
            },
            onSendEmail = { email ->
                val mailUri = ("mailto:" + email.address).toUri()
                val intent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                    email.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    email.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                }
                runIntent(context, intent)
            },
            onDialPhone = { number ->
                runIntent(context, Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
            },
            onSendSms = { sms ->
                val intent = Intent(Intent.ACTION_SENDTO, "smsto:${sms.number}".toUri()).apply {
                    sms.body?.let { putExtra("sms_body", it) }
                }
                runIntent(context, intent)
            },
            onOpenGeo = { uri ->
                runIntent(context, Intent(Intent.ACTION_VIEW, uri.toUri()))
            },
            onConnectWifi = { wifi ->
                val result = WifiConnector.connect(context, wifi)
                val msg = when (result) {
                    is WifiConnector.Result.Launched -> "已唤起系统连接面板"
                    is WifiConnector.Result.Suggested -> result.message
                    is WifiConnector.Result.FellBackToSettings -> result.message
                    is WifiConnector.Result.Failed -> result.message
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultBody(
    paddingValues: PaddingValues,
    format: String,
    rawValue: String,
    parsed: QrContent,
    platformInstalled: Boolean,
    onCopy: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onShare: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onLaunchInWeChat: (String) -> Unit,
    onLaunchInQq: (String) -> Unit,
    onLaunchInAlipay: (String) -> Unit,
    onPlatformMissing: (String) -> Unit,
    onSendEmail: (QrContent.Email) -> Unit,
    onDialPhone: (String) -> Unit,
    onSendSms: (QrContent.Sms) -> Unit,
    onOpenGeo: (String) -> Unit,
    onConnectWifi: (QrContent.Wifi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        CategoryChipRow(parsed = parsed, format = format)

        Spacer(modifier = Modifier.height(16.dp))

        ContentCard(parsed = parsed, rawValue = rawValue)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "操作",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Category-specific primary action(s) come first.
            when (parsed) {
                is QrContent.Url -> {
                    Button(onClick = { onOpenInBrowser(parsed.url) }) {
                        LeadingIcon(Icons.Filled.OpenInBrowser)
                        Text("在浏览器打开")
                    }
                }
                is QrContent.WeChat -> {
                    val isHttp = looksLikeHttp(parsed.rawValue)
                    if (platformInstalled) {
                        Button(onClick = { onLaunchInWeChat(parsed.rawValue) }) {
                            LeadingIcon(Icons.AutoMirrored.Filled.OpenInNew)
                            Text("使用微信打开")
                        }
                        if (isHttp) {
                            OutlinedButton(onClick = { onOpenInBrowser(parsed.rawValue) }) {
                                LeadingIcon(Icons.Filled.OpenInBrowser)
                                Text("在浏览器打开")
                            }
                        }
                    } else if (isHttp) {
                        // WeChat is not installed; fall back to the browser
                        // as the primary action so the user has a working tap.
                        Button(onClick = { onOpenInBrowser(parsed.rawValue) }) {
                            LeadingIcon(Icons.Filled.OpenInBrowser)
                            Text("在浏览器打开")
                        }
                    } else {
                        // App missing AND the payload is a pure platform
                        // scheme (e.g. wxp://, weixin://). Render a visible
                        // affordance that explains the absence on tap so the
                        // user is not left wondering why no platform action
                        // appeared next to Copy / Share.
                        OutlinedButton(onClick = { onPlatformMissing("微信") }) {
                            LeadingIcon(Icons.AutoMirrored.Filled.OpenInNew)
                            Text("使用微信打开（未安装）")
                        }
                    }
                }
                is QrContent.Qq -> {
                    val isHttp = looksLikeHttp(parsed.rawValue)
                    if (platformInstalled) {
                        Button(onClick = { onLaunchInQq(parsed.rawValue) }) {
                            LeadingIcon(Icons.AutoMirrored.Filled.OpenInNew)
                            Text("使用QQ打开")
                        }
                        if (isHttp) {
                            OutlinedButton(onClick = { onOpenInBrowser(parsed.rawValue) }) {
                                LeadingIcon(Icons.Filled.OpenInBrowser)
                                Text("在浏览器打开")
                            }
                        }
                    } else if (isHttp) {
                        Button(onClick = { onOpenInBrowser(parsed.rawValue) }) {
                            LeadingIcon(Icons.Filled.OpenInBrowser)
                            Text("在浏览器打开")
                        }
                    } else {
                        OutlinedButton(onClick = { onPlatformMissing("QQ") }) {
                            LeadingIcon(Icons.AutoMirrored.Filled.OpenInNew)
                            Text("使用QQ打开（未安装）")
                        }
                    }
                }
                is QrContent.Alipay -> {
                    val isHttp = looksLikeHttp(parsed.rawValue)
                    if (platformInstalled) {
                        Button(onClick = { onLaunchInAlipay(parsed.rawValue) }) {
                            LeadingIcon(Icons.AutoMirrored.Filled.OpenInNew)
                            Text("使用支付宝打开")
                        }
                        if (isHttp) {
                            OutlinedButton(onClick = { onOpenInBrowser(parsed.rawValue) }) {
                                LeadingIcon(Icons.Filled.OpenInBrowser)
                                Text("在浏览器打开")
                            }
                        }
                    } else if (isHttp) {
                        Button(onClick = { onOpenInBrowser(parsed.rawValue) }) {
                            LeadingIcon(Icons.Filled.OpenInBrowser)
                            Text("在浏览器打开")
                        }
                    } else {
                        OutlinedButton(onClick = { onPlatformMissing("支付宝") }) {
                            LeadingIcon(Icons.AutoMirrored.Filled.OpenInNew)
                            Text("使用支付宝打开（未安装）")
                        }
                    }
                }
                is QrContent.Wifi -> {
                    // 使用最新推荐的 ACTION_WIFI_ADD_NETWORKS / NetworkSuggestion 流程
                    Button(onClick = { onConnectWifi(parsed) }) {
                        LeadingIcon(Icons.Filled.Wifi)
                        Text("连接")
                    }
                    val password = parsed.password
                    if (!password.isNullOrEmpty()) {
                        OutlinedButton(onClick = { onCopyText(password, "已复制Wi-Fi密码") }) {
                            LeadingIcon(Icons.Filled.ContentCopy)
                            Text("复制密码")
                        }
                    }
                }
                is QrContent.Email -> {
                    Button(onClick = { onSendEmail(parsed) }) {
                        LeadingIcon(Icons.Filled.Mail)
                        Text("发送邮件")
                    }
                }
                is QrContent.Phone -> {
                    Button(onClick = { onDialPhone(parsed.number) }) {
                        LeadingIcon(Icons.Filled.Phone)
                        Text("拨打电话")
                    }
                }
                is QrContent.Sms -> {
                    Button(onClick = { onSendSms(parsed) }) {
                        LeadingIcon(Icons.Filled.Sms)
                        Text("发送短信")
                    }
                }
                is QrContent.Geo -> {
                    Button(onClick = { onOpenGeo(parsed.rawValue) }) {
                        LeadingIcon(Icons.Filled.LocationOn)
                        Text("在地图中打开")
                    }
                }
                is QrContent.Contact, is QrContent.Text -> Unit
            }

            // Common actions are always available.
            FilledTonalButton(onClick = onCopy) {
                LeadingIcon(Icons.Filled.ContentCopy)
                Text("复制")
            }
            FilledTonalButton(onClick = onShare) {
                LeadingIcon(Icons.Filled.Share)
                Text("分享")
            }
        }
    }
}

@Composable
private fun CategoryChipRow(parsed: QrContent, format: String) {
    // Static category badge. M3 chips (Suggestion / Assist / Filter / Input)
    // all imply actionability or selection, so use a Surface shaped like a
    // chip instead. This keeps the visual weight of the previous chip while
    // making the affordance unambiguously informational for screen readers.
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = categoryIcon(parsed.category),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = categoryLabel(parsed.category, format),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ContentCard(parsed: QrContent, rawValue: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "内容",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Render category-specific structured fields when we have them,
            // and always include the raw value at the bottom for copy/share.
            when (parsed) {
                is QrContent.Wifi -> {
                    LabelledRow(label = "网络名称 (SSID)", value = parsed.ssid)
                    parsed.encryption?.let {
                        LabelledRow(label = "加密方式", value = encryptionLabel(it))
                    }
                    parsed.password?.let { LabelledRow(label = "密码", value = it) }
                    LabelledRow(
                        label = "隐藏网络",
                        value = if (parsed.hidden) "是" else "否",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is QrContent.Email -> {
                    LabelledRow(label = "收件人", value = parsed.address)
                    parsed.subject?.let { LabelledRow(label = "主题", value = it) }
                    parsed.body?.let { LabelledRow(label = "正文", value = it) }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is QrContent.Phone -> {
                    LabelledRow(label = "电话号码", value = parsed.number)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is QrContent.Sms -> {
                    LabelledRow(label = "号码", value = parsed.number)
                    parsed.body?.let { LabelledRow(label = "短信内容", value = it) }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is QrContent.Geo -> {
                    LabelledRow(
                        label = "坐标",
                        value = "${parsed.lat}, ${parsed.lng}",
                    )
                    parsed.query?.let { LabelledRow(label = "地点", value = it) }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                else -> Unit
            }

            SelectionContainer {
                Text(
                    text = rawValue,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LabelledRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .size(18.dp)
            .padding(end = 6.dp),
    )
}

private fun categoryLabel(category: QrCategory, format: String): String = when (category) {
    QrCategory.URL -> "网址"
    QrCategory.WECHAT -> "微信"
    QrCategory.QQ -> "QQ"
    QrCategory.ALIPAY -> "支付宝"
    QrCategory.WIFI -> "Wi-Fi"
    QrCategory.EMAIL -> "邮件"
    QrCategory.PHONE -> "电话"
    QrCategory.SMS -> "短信"
    QrCategory.GEO -> "位置"
    QrCategory.CONTACT -> "联系人"
    QrCategory.TEXT -> if (format.isNotBlank()) "文本 · $format" else "文本"
}

private fun categoryIcon(category: QrCategory): ImageVector = when (category) {
    QrCategory.URL -> Icons.Filled.Link
    QrCategory.WECHAT,
    QrCategory.QQ,
    QrCategory.ALIPAY -> Icons.AutoMirrored.Filled.OpenInNew
    QrCategory.WIFI -> Icons.Filled.Wifi
    QrCategory.EMAIL -> Icons.Filled.Mail
    QrCategory.PHONE -> Icons.Filled.Phone
    QrCategory.SMS -> Icons.Filled.Sms
    QrCategory.GEO -> Icons.Filled.LocationOn
    QrCategory.CONTACT -> Icons.Filled.ContactPhone
    QrCategory.TEXT -> Icons.Filled.QrCode2
}

private fun looksLikeHttp(value: String): Boolean {
    val lower = value.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}

/**
 * Map the raw `T:` token from a `WIFI:` QR code to a human-readable label.
 * The standard tokens are `nopass`, `WPA` (covers WPA / WPA2 / WPA-PSK),
 * `WPA2`, `WPA3`, `WEP` and `SAE`. Unknown tokens are returned unchanged so
 * an unfamiliar value still appears under "加密方式" rather than disappearing.
 */
private fun encryptionLabel(raw: String): String {
    return when (raw.trim().uppercase()) {
        "NOPASS", "NONE", "" -> "无密码"
        "WPA" -> "WPA / WPA2"
        "WPA2" -> "WPA2"
        "WPA3" -> "WPA3"
        "WPA-EAP" -> "WPA-EAP (企业)"
        "WPA2-EAP" -> "WPA2-EAP (企业)"
        "WEP" -> "WEP"
        "SAE" -> "WPA3 (SAE)"
        else -> raw
    }
}

private fun runIntent(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "无法打开: ${e.message ?: "未知错误"}",
            Toast.LENGTH_SHORT,
        ).show()
    }
}
