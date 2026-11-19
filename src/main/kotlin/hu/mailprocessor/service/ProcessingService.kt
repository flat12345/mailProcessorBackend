package hu.mailprocessor.service

import hu.mailprocessor.ai.AiProvider
import hu.mailprocessor.ai.MailInput
import hu.mailprocessor.config.AppProperties
import hu.mailprocessor.config.ReplyMode
import hu.mailprocessor.config.RuntimeSettings
import hu.mailprocessor.domain.AiResult
import hu.mailprocessor.domain.AiResultRepository
import hu.mailprocessor.domain.Category
import hu.mailprocessor.domain.CategoryRepository
import hu.mailprocessor.domain.EmailMessage
import hu.mailprocessor.domain.EmailRepository
import hu.mailprocessor.domain.EmailStatus
import hu.mailprocessor.mail.GmailService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProcessingService(
    private val props: AppProperties,
    private val settings: RuntimeSettings,
    private val providers: List<AiProvider>,
    private val categories: CategoryRepository,
    private val emails: EmailRepository,
    private val results: AiResultRepository,
    private val gmail: GmailService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun activeProviders(): List<AiProvider> {
        val chosen = if (settings.testMode) providers else providers.filter { it.name == settings.activeProvider }
        val usable = chosen.filter { it.available }
        if (usable.size < chosen.size) {
            log.warn("Nem elérhető (nincs API-kulcs): {}", chosen.filter { !it.available }.map { it.name })
        }
        return usable
    }

    /** Egy levél feldolgozása a jelenlegi módnak megfelelően. Az eredmények mentve vannak. */
    fun process(email: EmailMessage): List<AiResult> {
        val cats = categories.findByActiveTrue()
        val selectedProviders = activeProviders()
        if (selectedProviders.isEmpty()) {
            email.status = EmailStatus.FAILED
            emails.save(email)
            throw IllegalStateException("Nincs használható AI szolgáltató (API-kulcs / ACTIVE_PROVIDER ellenőrzése)")
        }

        val mail = MailInput(email.fromAddress, email.subject, email.body)
        val out = selectedProviders.map { runProvider(it, email, mail, cats) }

        if (!settings.testMode) {
            val winner = out.firstOrNull { it.error == null }
            if (winner != null) {
                winner.selected = true
                email.category = winner.category
                maybeSendReply(email, winner)
            }
        } else {
            sendTestReviews(email, out)
        }

        results.saveAll(out)
        email.status = if (out.any { it.error == null }) EmailStatus.PROCESSED else EmailStatus.FAILED
        emails.save(email)
        return out
    }

    /** Mentés nélküli próbafuttatás az összes elérhető AI-val (a /api/ai/dry-run használja). */
    fun dryRun(mail: MailInput): Map<String, Any?> {
        val cats = categories.findByActiveTrue()
        return providers.filter { it.available }.associate { p ->
            p.name to try {
                p.process(mail, cats)
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun runProvider(p: AiProvider, email: EmailMessage, mail: MailInput, cats: List<Category>): AiResult {
        val r = AiResult(emailId = email.id!!, provider = p.name)
        try {
            val o = p.process(mail, cats)
            r.category = o.category
            r.confidence = o.confidence
            r.extractedJson = o.extractedJson
            r.replyText = o.reply
            r.needsHuman = o.needsHuman
            r.rawResponse = o.raw
            r.inputTokens = o.inputTokens
            r.outputTokens = o.outputTokens
            r.latencyMs = o.latencyMs
        } catch (e: Exception) {
            log.error("{} hiba a(z) {} levélnél: {}", p.name, email.id, e.message)
            r.error = (e.message ?: e.javaClass.simpleName).take(2000)
        }
        return r
    }

    private fun maybeSendReply(email: EmailMessage, r: AiResult) {
        if (props.replyMode != ReplyMode.SEND || !gmail.enabled) return
        if (r.needsHuman || r.replyText.isNullOrBlank()) return
        try {
            gmail.send(
                to = GmailService.plainAddress(email.fromAddress),
                subject = replySubject(email.subject),
                body = r.replyText!!,
                threadId = email.threadId,
                inReplyTo = email.messageIdHeader,
            )
            r.replySent = true
        } catch (e: Exception) {
            log.error("Válasz küldése sikertelen ({}): {}", email.id, e.message)
        }
    }

    /** Teszt módban az ügyfél SOHA nem kap levelet; az AI-válaszok a TEST_REVIEW_EMAIL címre mennek összehasonlításra. */
    private fun sendTestReviews(email: EmailMessage, out: List<AiResult>) {
        if (props.replyMode != ReplyMode.SEND || !gmail.enabled || props.testReviewEmail.isBlank()) return
        out.filter { it.error == null && !it.replyText.isNullOrBlank() }.forEach { r ->
            try {
                gmail.send(
                    to = props.testReviewEmail,
                    subject = "[TEST ${r.provider}] ${replySubject(email.subject)}",
                    body = "Eredeti feladó: ${email.fromAddress}\nKategória: ${r.category}\nBiztonság: ${r.confidence}\n" +
                        "Emberi átnézés kell: ${r.needsHuman}\nKinyert adatok: ${r.extractedJson}\n\n--- Javasolt válasz ---\n${r.replyText}",
                )
            } catch (e: Exception) {
                log.error("Teszt-értesítés küldése sikertelen ({}): {}", email.id, e.message)
            }
        }
    }

    private fun replySubject(s: String) = if (s.startsWith("Re:", ignoreCase = true)) s else "Re: $s"
}
