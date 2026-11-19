package hu.mailprocessor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class Application

fun main(args: Array<String>) {
    // Diagnosztika: melyik adatbázisra próbál csatlakozni (jelszó nélkül)
    fun env(n: String) = System.getenv(n)?.takeIf { it.isNotBlank() } ?: "(NINCS BEÁLLÍTVA)"
    println(
        "DB-CONFIG host=${env("MYSQLHOST")} port=${env("MYSQLPORT")} " +
            "db=${env("MYSQLDATABASE")} user=${env("MYSQLUSER")} password=${if (System.getenv("MYSQLPASSWORD").isNullOrBlank()) "(NINCS)" else "(beállítva)"}"
    )
    runApplication<Application>(*args)
}
