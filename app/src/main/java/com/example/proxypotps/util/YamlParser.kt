package com.example.proxypotps.util

import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import org.yaml.snakeyaml.Yaml

object YamlParser {
    fun parseProxyNodes(yamlText: String): List<ProxyNode> {
        if (yamlText.isBlank()) return emptyList()
        val yaml = Yaml()
        val raw = yaml.load<Any>(yamlText) as? Map<*, *> ?: return emptyList()
        val proxies = raw["proxies"] as? List<*> ?: return emptyList()
        return proxies.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"]?.toString() ?: return@mapNotNull null
            val type = map["type"]?.toString() ?: "unknown"
            val server = map["server"]?.toString() ?: ""
            val port = map["port"]?.toString()?.toIntOrNull() ?: 0
            val extras = map
                .filterKeys { it !in setOf("name", "type", "server", "port") }
                .map { (key, value) -> key.toString() to value.toString() }
                .toMap()
            ProxyNode(
                name = name,
                type = type,
                server = server,
                port = port,
                extras = extras,
                status = NodeStatus.UNKNOWN,
                latencyMs = null
            )
        }
    }
}
