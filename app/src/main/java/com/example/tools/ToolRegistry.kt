package com.example.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ToolRegistry(tools: List<AgentTool>) {
    private val toolMap: Map<String, AgentTool> = tools.associateBy { it.definition.name }

    fun allDefinitions(): List<ToolDefinition> = toolMap.values.map { it.definition }

    fun getTool(name: String): AgentTool? = toolMap[name]

    fun validateArguments(toolName: String, rawArgsJson: String): Result<JsonObject> {
        val tool = toolMap[toolName]
            ?: return Result.failure(IllegalArgumentException("Unknown tool '$toolName'."))

        val parsed = runCatching {
            val trimmed = rawArgsJson.trim()
            if (trimmed.isEmpty()) JsonObject(emptyMap())
            else Json.parseToJsonElement(trimmed) as JsonObject
        }.getOrElse {
            return Result.failure(IllegalArgumentException("Invalid JSON arguments for '$toolName'."))
        }

        // Validate required parameters and enforce basic safety bounds
        for (param in tool.definition.parameters) {
            if (param.required && !parsed.containsKey(param.name)) {
                return Result.failure(
                    IllegalArgumentException("Missing required parameter '${param.name}' for '$toolName'.")
                )
            }
        }
        val serializedSize = parsed.toString().length
        if (serializedSize > 60_000) {
            return Result.failure(IllegalArgumentException("Tool argument payload exceeds 60KB limit."))
        }
        return Result.success(parsed)
    }

    fun toOpenAiToolsSchemaJson(disabledTools: Set<String>): JsonArray {
        return buildJsonArray {
            toolMap.values
                .filter { it.definition.name !in disabledTools }
                .forEach { tool ->
                    val def = tool.definition
                    add(buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", def.name)
                            put("description", def.description)
                            putJsonObject("parameters") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    def.parameters.forEach { param ->
                                        putJsonObject(param.name) {
                                            put("type", param.type)
                                            put("description", param.description)
                                        }
                                    }
                                }
                                put("required", buildJsonArray {
                                    def.parameters.filter { it.required }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) }
                                })
                            }
                        }
                    })
                }
        }
    }

    fun buildPromptToolCatalog(disabledTools: Set<String>): String {
        return toolMap.values
            .filter { it.definition.name !in disabledTools }
            .joinToString("\n") { tool ->
                val params = tool.definition.parameters.joinToString(", ") {
                    "${it.name}:${it.type}${if (it.required) "*" else ""}"
                }
                "- ${tool.definition.name}($params): ${tool.definition.description}"
            }
    }
}
