package hu.mailprocessor.service

import hu.mailprocessor.domain.EmailMessage
import hu.mailprocessor.domain.EmailRepository
import hu.mailprocessor.mail.GmailService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MailPoller(
    private val gmail: GmailService,
    private val emails: EmailRepository,
    private val processing: ProcessingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.poll-interval-ms}", initialDelay = 15000)
    fun poll() {
        if (!gmail.enabled) return
        try {
            gmail.fetchUnread(20).forEach { m ->
                if (!emails.existsByGmailId(m.gmailId)) {
                    val saved = emails.save(
                        EmailMessage(
                            gmailId = m.gmailId,
                            threadId = m.threadId,
                            messageIdHeader = m.messageIdHeader,
                            fromAddress = m.from,
                            subject = m.subject,
                            body = m.body,
                            receivedAt = m.receivedAt,
                        )
                    )
                    try {
                        processing.process(saved)
                    } catch (e: Exception) {
                        log.error("Feldolgozás sikertelen ({}): {}", saved.id, e.message)
                    }
                }
                // Mentés után mindig olvasottra állítjuk, hogy ne dolgozzuk fel újra; a hibás levelek FAILED státusszal újrafuttathatók.
                gmail.markRead(m.gmailId)
            }
        } catch (e: Exception) {
            log.error("Gmail lekérdezés hiba: {}", e.message)
        }
    }
}
