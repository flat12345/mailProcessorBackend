package hu.mailprocessor.mail

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.ModifyMessageRequest
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import hu.mailprocessor.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

data class IncomingMail(
    val gmailId: String,
    val threadId: String?,
    val messageIdHeader: String?,
    val from: String,
    val subject: String,
    val body: String,
    val receivedAt: Instant,
)

@Service
class GmailService(private val props: AppProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    val enabled: Boolean get() = props.gmail.enabled

    private val gmail: Gmail by lazy {
        val creds = UserCredentials.newBuilder()
            .setClientId(props.gmail.clientId)
            .setClientSecret(props.gmail.clientSecret)
            .setRefreshToken(props.gmail.refreshToken)
            .build()
        Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), HttpCredentialsAdapter(creds))
            .setApplicationName("mail-processor")
            .build()
    }

    fun fetchUnread(max: Long): List<IncomingMail> {
        val list = gmail.users().messages().list("me")
            .setQ("is:unread in:inbox")
            .setMaxResults(max)
            .execute()
        return (list.messages ?: emptyList()).mapNotNull { stub ->
            val msg = gmail.users().messages().get("me", stub.id).setFormat("full").execute()
            toIncoming(msg)
        }
    }

    private fun toIncoming(msg: Message): IncomingMail? {
        val headers = msg.payload?.headers ?: emptyList()
        fun h(name: String): String? = headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

        val from = h("From") ?: return null
        // Válaszhurok és automata levelek elkerülése
        val autoSubmitted = h("Auto-Submitted")
        val precedence = h("Precedence")?.lowercase()
        val isAuto = (autoSubmitted != null && !autoSubmitted.equals("no", ignoreCase = true)) ||
            precedence in setOf("bulk", "junk", "list")
        val fromSelf = props.gmail.address.isNotBlank() && from.contains(props.gmail.address, ignoreCase = true)
        if (isAuto || fromSelf) {
            log.info("Kihagyva automata/saját levél: {}", msg.id)
            markRead(msg.id)
            return null
        }

        val body = extractText(msg.payload) ?: msg.snippet ?: ""
        return IncomingMail(
            gmailId = msg.id,
            threadId = msg.threadId,
            messageIdHeader = h("Message-ID")?.take(255),
            from = from.take(320),
            subject = (h("Subject") ?: "(nincs tárgy)").take(500),
            body = body,
            receivedAt = Instant.ofEpochMilli(msg.internalDate ?: System.currentTimeMillis()),
        )
    }

    private fun extractText(part: MessagePart?): String? {
        if (part == null) return null
        if (part.mimeType == "text/plain" && part.body?.data != null) {
            return String(part.body.decodeData(), Charsets.UTF_8)
        }
        part.parts?.forEach { child -> extractText(child)?.let { return it } }
        return null
    }

    fun markRead(gmailId: String) {
        gmail.users().messages().modify("me", gmailId, ModifyMessageRequest().setRemoveLabelIds(listOf("UNREAD"))).execute()
    }

    /** Egyszerű text/plain levél küldése (opcionálisan meglévő szálba). */
    fun send(to: String, subject: String, body: String, threadId: String? = null, inReplyTo: String? = null) {
        fun clean(s: String) = s.replace("\r", "").replace("\n", " ").trim()
        val subjectEnc = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean(subject).toByteArray(Charsets.UTF_8)) + "?="
        val bodyEnc = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(body.toByteArray(Charsets.UTF_8))
        val headers = buildList {
            add("To: ${clean(to)}")
            add("Subject: $subjectEnc")
            if (!inReplyTo.isNullOrBlank()) {
                add("In-Reply-To: ${clean(inReplyTo)}")
                add("References: ${clean(inReplyTo)}")
            }
            add("MIME-Version: 1.0")
            add("Content-Type: text/plain; charset=UTF-8")
            add("Content-Transfer-Encoding: base64")
        }
        val raw = headers.joinToString("\r\n") + "\r\n\r\n" + bodyEnc
        val message = Message().setRaw(Base64.getUrlEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8)))
        if (!threadId.isNullOrBlank()) message.threadId = threadId
        gmail.users().messages().send("me", message).execute()
    }

    companion object {
        private val ADDR = Regex("<([^>]+)>")
        fun plainAddress(from: String): String = ADDR.find(from)?.groupValues?.get(1) ?: from.trim()
    }
}
