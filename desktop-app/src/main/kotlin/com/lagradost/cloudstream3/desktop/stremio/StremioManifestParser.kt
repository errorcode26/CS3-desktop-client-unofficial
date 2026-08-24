package com.lagradost.cloudstream3.desktop.stremio

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object StremioManifestParser {
    private val mapper = jacksonObjectMapper()

    fun parse(manifestUrl: String, jsonText: String): StremioManifest {
        val root = mapper.readTree(jsonText)
        val id = root["id"]?.asText() ?: ""
        val name = root["name"]?.asText() ?: id
        val description = root["description"]?.asText() ?: ""
        val version = root["version"]?.asText() ?: "1.0.0"
        val logoUrl = root["logo"]?.asText()

        val defaultTypes = root["types"]?.mapNotNull { it.asText() } ?: emptyList()
        val defaultPrefixes = root["idPrefixes"]?.mapNotNull { it.asText() } ?: emptyList()

        val resources = mutableListOf<StremioResource>()
        root["resources"]?.forEach { node ->
            if (node.isTextual) {
                resources.add(
                    StremioResource(
                        name = node.asText(),
                        types = defaultTypes,
                        idPrefixes = defaultPrefixes,
                    )
                )
            } else if (node.isObject) {
                val resName = node["name"]?.asText() ?: ""
                val resTypes = node["types"]?.mapNotNull { it.asText() } ?: defaultTypes
                val resPrefixes = node["idPrefixes"]?.mapNotNull { it.asText() } ?: defaultPrefixes
                if (resName.isNotBlank()) {
                    resources.add(
                        StremioResource(
                            name = resName,
                            types = resTypes,
                            idPrefixes = resPrefixes,
                        )
                    )
                }
            }
        }

        val behaviorHintsNode = root["behaviorHints"]
        val behaviorHints = if (behaviorHintsNode != null && behaviorHintsNode.isObject) {
            StremioBehaviorHints(
                configurable = behaviorHintsNode["configurable"]?.asBoolean() ?: false,
                configurationRequired = behaviorHintsNode["configurationRequired"]?.asBoolean() ?: false,
                adult = behaviorHintsNode["adult"]?.asBoolean() ?: false,
                p2p = behaviorHintsNode["p2p"]?.asBoolean() ?: false,
            )
        } else {
            StremioBehaviorHints()
        }

        return StremioManifest(
            id = id,
            name = name,
            description = description,
            version = version,
            logoUrl = logoUrl,
            resources = resources,
            types = defaultTypes,
            idPrefixes = defaultPrefixes,
            behaviorHints = behaviorHints,
            transportUrl = manifestUrl,
        )
    }
}
