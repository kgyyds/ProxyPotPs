package com.example.proxypotps.network

import com.example.proxypotps.domain.model.ProxyNode
import java.util.Locale

data class ProtocolSupportIssue(
    val code: String,
    val detail: String
)

class UnsupportedProtocolException(
    val issue: ProtocolSupportIssue
) : IllegalStateException(issue.code)

fun ProxyNode.protocolSupportIssue(): ProtocolSupportIssue? {
    val type = this.type.lowercase(Locale.US)
    return when (type) {
        "ss", "shadowsocks" -> null
        "trojan" -> {
            if (extras["network"]?.lowercase(Locale.US) == "grpc") {
                ProtocolSupportIssue(code = "UNSUPPORTED_PROTOCOL", detail = "trojan(grpc)")
            } else {
                null
            }
        }
        else -> ProtocolSupportIssue(code = "UNSUPPORTED_PROTOCOL", detail = type)
    }
}

fun ProxyNode.statusReasonText(): String? {
    val reason = statusReason ?: return null
    if (reason.startsWith("UNSUPPORTED_PROTOCOL:")) {
        return "类型不支持：${reason.substringAfter(":")}" 
    }
    if (reason == "MISSING_LOCAL_PROXY_PORT") {
        return "本地代理端口缺失"
    }
    return null
}

