package hu.mailprocessor.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

enum class ReplyMode { OFF, SEND }

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val apiKey: String = "",
    val testMode: Boolean = false,
    val activeProvider: String = "claude",
    val replyMode: ReplyMode = ReplyMode.OFF,
    val testReviewEmail: String = "",
    val pollIntervalMs: Long = 60000,
    val claude: ProviderProps = ProviderProps(),
    val gemini: ProviderProps = ProviderProps(),
    val gmail: GmailProps = GmailProps(),
)

data class ProviderProps(val apiKey: String = "", val model: String = "")

data class GmailProps(
    val clientId: String = "",
    val clientSecret: String = "",
    val refreshToken: String = "",
    val address: String = "",
) {
    val enabled: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank() && refreshToken.isNotBlank()
}

/** Futásidőben (újraindítás nélkül) állítható beállítások. Újraindításkor a környezeti változó értékére áll vissza. */
@Component
class RuntimeSettings(props: AppProperties) {
    @Volatile var testMode: Boolean = props.testMode
    @Volatile var activeProvider: String = props.activeProvider.lowercase()
}

/** Az /api alatti végpontokat X-API-Key fejléccel védi. Ha nincs beállított kulcs, mindent tilt (fail closed). */
@Component
class ApiKeyFilter(private val props: AppProperties) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val provided = request.getHeader("X-API-Key") ?: ""
        val ok = props.apiKey.isNotBlank() &&
            MessageDigest.isEqual(provided.toByteArray(), props.apiKey.toByteArray())
        if (!ok) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error":"unauthorized"}""")
            return
        }
        chain.doFilter(request, response)
    }
}
