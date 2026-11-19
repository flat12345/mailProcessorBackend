package hu.mailprocessor.web

import hu.mailprocessor.ai.AiProvider
import hu.mailprocessor.ai.MailInput
import hu.mailprocessor.config.RuntimeSettings
import hu.mailprocessor.domain.AiResult
import hu.mailprocessor.domain.EmailRepository
import hu.mailprocessor.service.ProcessingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ConfigUpdate(val testMode: Boolean? = null, val activeProvider: String? = null)
data class DryRunRequest(val from: String = "teszt@example.com", val subject: String = "", val body: String = "")

/** Az AI-val kommunikáló API. */
@RestController
@RequestMapping("/api/ai")
class AiController(
    private val settings: RuntimeSettings,
    private val providers: List<AiProvider>,
    private val processing: ProcessingService,
    private val emails: EmailRepository,
) {
    @GetMapping("/config")
    fun config() = mapOf(
        "testMode" to settings.testMode,
        "activeProvider" to settings.activeProvider,
        "providers" to providers.associate { it.name to it.available },
    )

    /** Futásidejű átkapcsolás (újraindításig érvényes; tartós beállítás: TEST_MODE / ACTIVE_PROVIDER env). */
    @PutMapping("/config")
    fun update(@RequestBody u: ConfigUpdate): ResponseEntity<Any> {
        u.activeProvider?.let { p ->
            if (providers.none { it.name == p.lowercase() }) {
                return ResponseEntity.badRequest().body(mapOf("error" to "ismeretlen provider: $p"))
            }
            settings.activeProvider = p.lowercase()
        }
        u.testMode?.let { settings.testMode = it }
        return ResponseEntity.ok(config())
    }

    /** Egy már tárolt levél újrafeldolgozása a jelenlegi módban. */
    @PostMapping("/process/{emailId}")
    fun reprocess(@PathVariable emailId: Long): ResponseEntity<Any> {
        val email = emails.findById(emailId).orElse(null) ?: return ResponseEntity.notFound().build()
        val out: List<AiResult> = processing.process(email)
        return ResponseEntity.ok(out)
    }

    /** Mentés és küldés nélküli próba: szöveg be, minden elérhető AI válasza ki. */
    @PostMapping("/dry-run")
    fun dryRun(@RequestBody r: DryRunRequest) = processing.dryRun(MailInput(r.from, r.subject, r.body))
}
