package hu.mailprocessor.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

enum class EmailStatus { NEW, PROCESSED, FAILED }

@Entity
@Table(name = "categories")
class Category(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, unique = true) var name: String = "",
    @Column(columnDefinition = "TEXT") var description: String? = null,
    /** Szabad szöveges leírás arról, milyen adatokat kell kinyerni ebből a típusból (pl. "rendelésszám, név, telefonszám"). */
    @Column(columnDefinition = "TEXT") var extractFields: String? = null,
    var active: Boolean = true,
)

@Entity
@Table(name = "email_messages")
class EmailMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, unique = true) var gmailId: String = "",
    var threadId: String? = null,
    var messageIdHeader: String? = null,
    @Column(nullable = false) var fromAddress: String = "",
    @Column(nullable = false) var subject: String = "",
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT") var body: String = "",
    @Column(nullable = false) var receivedAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: EmailStatus = EmailStatus.NEW,
    var category: String? = null,
)

@Entity
@Table(name = "ai_results")
class AiResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false) var emailId: Long = 0,
    @Column(nullable = false) var provider: String = "",
    var category: String? = null,
    var confidence: Double? = null,
    @Column(columnDefinition = "MEDIUMTEXT") var extractedJson: String? = null,
    @Column(columnDefinition = "MEDIUMTEXT") var replyText: String? = null,
    @Column(nullable = false) var needsHuman: Boolean = false,
    @Column(columnDefinition = "MEDIUMTEXT") var rawResponse: String? = null,
    var inputTokens: Int? = null,
    var outputTokens: Int? = null,
    var latencyMs: Long? = null,
    @Column(columnDefinition = "TEXT") var error: String? = null,
    @Column(nullable = false) var selected: Boolean = false,
    @Column(nullable = false) var replySent: Boolean = false,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
)

interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByActiveTrue(): List<Category>
}

interface EmailRepository : JpaRepository<EmailMessage, Long> {
    fun existsByGmailId(gmailId: String): Boolean
    fun findByStatus(status: EmailStatus, pageable: Pageable): Page<EmailMessage>
}

interface AiResultRepository : JpaRepository<AiResult, Long> {
    fun findByEmailIdOrderByIdAsc(emailId: Long): List<AiResult>

    /** provider, darab, átlag késleltetés ms, összes input token, összes output token, hibák száma */
    @Query(
        "select r.provider, count(r), avg(r.latencyMs), sum(r.inputTokens), sum(r.outputTokens), " +
            "sum(case when r.error is not null then 1 else 0 end) from AiResult r group by r.provider"
    )
    fun providerStats(): List<Array<Any?>>
}
