package hu.mailprocessor.web

import hu.mailprocessor.domain.AiResult
import hu.mailprocessor.domain.AiResultRepository
import hu.mailprocessor.domain.Category
import hu.mailprocessor.domain.CategoryRepository
import hu.mailprocessor.domain.EmailMessage
import hu.mailprocessor.domain.EmailRepository
import hu.mailprocessor.domain.EmailStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")
}

/** Az adatbázist kezelő REST API. */
@RestController
@RequestMapping("/api/db")
class DbController(
    private val emails: EmailRepository,
    private val results: AiResultRepository,
    private val categories: CategoryRepository,
) {
    @GetMapping("/emails")
    fun listEmails(
        @RequestParam(required = false) status: EmailStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<EmailMessage> {
        val pr = PageRequest.of(page, size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "id"))
        return if (status != null) emails.findByStatus(status, pr) else emails.findAll(pr)
    }

    @GetMapping("/emails/{id}")
    fun getEmail(@PathVariable id: Long): ResponseEntity<EmailMessage> =
        emails.findById(id).map { ResponseEntity.ok(it) }.orElse(ResponseEntity.notFound().build())

    @GetMapping("/emails/{id}/results")
    fun emailResults(@PathVariable id: Long): List<AiResult> = results.findByEmailIdOrderByIdAsc(id)

    /** Szolgáltatónkénti összesítés a döntéshez: darabszám, átlag késleltetés, tokenek, hibák. */
    @GetMapping("/stats/providers")
    fun stats(): List<Map<String, Any?>> = results.providerStats().map {
        mapOf(
            "provider" to it[0], "count" to it[1], "avgLatencyMs" to it[2],
            "inputTokens" to it[3], "outputTokens" to it[4], "errors" to it[5],
        )
    }

    @GetMapping("/categories")
    fun listCategories(): List<Category> = categories.findAll()

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@RequestBody c: Category): Category {
        c.id = null
        return categories.save(c)
    }

    @PutMapping("/categories/{id}")
    fun updateCategory(@PathVariable id: Long, @RequestBody c: Category): ResponseEntity<Category> {
        val existing = categories.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        existing.name = c.name
        existing.description = c.description
        existing.extractFields = c.extractFields
        existing.active = c.active
        return ResponseEntity.ok(categories.save(existing))
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(@PathVariable id: Long) = categories.deleteById(id)
}
