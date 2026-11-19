# mailProcessorBackend

Spring Boot (Kotlin) backend, amely egy support e-mail címre érkező leveleket dolgoz fel AI-val (Claude és/vagy Gemini), a kinyert adatokat MySQL-be menti, és opcionálisan válaszol. Railway-re készült.

## Felépítés

| Réteg | Hol | Mit csinál |
|---|---|---|
| DB REST API | `/api/db/**` | levelek, AI-eredmények, kategóriák, szolgáltatói statisztika |
| AI API | `/api/ai/**` | config/teszt mód kapcsoló, újrafeldolgozás, dry-run |
| Gmail poller | `MailPoller` | percenként (állítható) lekéri az olvasatlan leveleket és feldolgoztatja |
| AI szolgáltatók | `ClaudeProvider`, `GeminiProvider` | közös `AiProvider` interfész mögött |

Minden `/api/**` hívás `X-API-Key: <APP_API_KEY>` fejlécet kér. A `/health` nyitott (Railway healthcheck).

## Teszt mód (`TEST_MODE`)

- `TEST_MODE=true`: minden elérhető AI feldolgozza ugyanazt a levelet; mindkét eredmény mentődik az `ai_results` táblába. **Az ügyfél nem kap választ**, a javasolt válaszok a `TEST_REVIEW_EMAIL` címre mennek (ha `REPLY_MODE=SEND`).
- `TEST_MODE=false`: csak az `ACTIVE_PROVIDER` (`claude` | `gemini`) fut, a válasz `REPLY_MODE=SEND` esetén az ügyfélnek megy (kivéve, ha az AI `needs_human`-nak jelöli).
- Futás közben átkapcsolható újraindítás nélkül: `PUT /api/ai/config {"testMode":false,"activeProvider":"claude"}` (újraindításig érvényes).
- Összehasonlítás: `GET /api/db/stats/providers` és `GET /api/db/emails/{id}/results`.

## Környezeti változók (Railway → Variables)

| Változó | Kötelező | Leírás |
|---|---|---|
| `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD` | igen | A Railway MySQL szolgáltatás változóira hivatkozz (`${{MySQL.MYSQLHOST}}` stb.) |
| `APP_API_KEY` | igen | Hosszú, véletlen string az API védelméhez |
| `ANTHROPIC_API_KEY` | ha Claude kell | Anthropic Console API-kulcs |
| `CLAUDE_MODEL` | nem | alapértelmezett: `claude-haiku-5-5` |
| `GEMINI_API_KEY` | ha Gemini kell | Google AI Studio kulcs |
| `GEMINI_MODEL` | nem | alapértelmezett: `gemini-2.5-flash` |
| `TEST_MODE` | nem | `true` / `false` |
| `ACTIVE_PROVIDER` | nem | `claude` / `gemini` |
| `REPLY_MODE` | nem | `OFF` (alap) / `SEND` |
| `TEST_REVIEW_EMAIL` | nem | teszt módban ide mennek az AI-válaszok |
| `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN` | a levélfeldolgozáshoz | Google Cloud OAuth kliens + a support postafiók refresh tokenje (scope: `gmail.modify` és `gmail.send`) |
| `GMAIL_ADDRESS` | ajánlott | a support cím (a saját levelek kihagyásához) |

Gmail nélkül az app elindul, de nem pollol; a `/api/ai/dry-run` és a DB API ilyenkor is használható.

## Railway deploy

1. Railway → New Project → Deploy from GitHub repo → ez a repo (a `Dockerfile` és `railway.json` alapján épít).
2. Add hozzá a MySQL szolgáltatást, majd állítsd be a fenti változókat.
3. A séma automatikusan létrejön induláskor (Flyway, `V1__init.sql`).
4. Töltsd fel a kategóriákat: `POST /api/db/categories` (`name`, `description`, `extractFields`).

## Gyors teszt (Gmail nélkül)

```bash
curl -X POST https://<app>.up.railway.app/api/ai/dry-run \
  -H "X-API-Key: $APP_API_KEY" -H "Content-Type: application/json" \
  -d '{"subject":"Hol a rendelésem?","body":"Szia, a 12345-ös rendelésem nem érkezett meg. Kovács Anna"}'
```

## Fontos

- A kódot még nem fordítottam le (a fejlesztői környezetben a Maven nem volt elérhető), az első `gradle bootJar` futás javításokat kérhet.
- A Gemini/Claude modellnevek és árak változnak, ellenőrizd az aktuálisakat.
