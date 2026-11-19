package hu.mailprocessor.ai

import com.fasterxml.jackson.databind.JsonNode
import hu.mailprocessor.config.AppProperties
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

private fun httpClient(): RestClient {
    val factory = JdkClientHttpRequestFactory(HttpClient.newHttpClient())
    factory.setReadTimeout(Duration.ofSeconds(90))
    return RestClient.builder().requestFactory(factory).build()
}

@Component
class ClaudeProvider(private val props: AppProperties) : BaseAiProvider() {
    override val name = "claude"
    override val available: Boolean get() = props.claude.apiKey.isNotBlank()
    private val client = httpClient()

    override fun call(system: String, user: String): RawAiResponse {
        val body = mapOf(
            "model" to props.claude.model,
            "max_tokens" to 2000,
            "system" to system,
            "messages" to listOf(mapOf("role" to "user", "content" to user)),
        )
        val res = client.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", props.claude.apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java) ?: error("Üres Claude válasz")
        val text = res.path("content").path(0).path("text").asText("")
        require(text.isNotBlank()) { "A Claude nem adott szöveges választ: $res" }
        return RawAiResponse(
            text = text,
            inputTokens = res.path("usage").path("input_tokens").takeIf { it.isNumber }?.asInt(),
            outputTokens = res.path("usage").path("output_tokens").takeIf { it.isNumber }?.asInt(),
        )
    }
}

@Component
class GeminiProvider(private val props: AppProperties) : BaseAiProvider() {
    override val name = "gemini"
    override val available: Boolean get() = props.gemini.apiKey.isNotBlank()
    private val client = httpClient()

    override fun call(system: String, user: String): RawAiResponse {
        val body = mapOf(
            "system_instruction" to mapOf("parts" to listOf(mapOf("text" to system))),
            "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to user)))),
            "generationConfig" to mapOf("responseMimeType" to "application/json", "temperature" to 0.2),
        )
        val res = client.post()
            .uri("https://generativelanguage.googleapis.com/v1beta/models/${props.gemini.model}:generateContent")
            .header("x-goog-api-key", props.gemini.apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java) ?: error("Üres Gemini válasz")
        val text = res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("")
        require(text.isNotBlank()) { "A Gemini nem adott szöveges választ: $res" }
        return RawAiResponse(
            text = text,
            inputTokens = res.path("usageMetadata").path("promptTokenCount").takeIf { it.isNumber }?.asInt(),
            outputTokens = res.path("usageMetadata").path("candidatesTokenCount").takeIf { it.isNumber }?.asInt(),
        )
    }
}
