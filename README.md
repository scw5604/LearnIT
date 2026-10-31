# LearnIT

LearnIT is a proof-of-concept for the IST 440W capstone. A student tries to clear a course blocker before a person is involved. They name the blocker with a course, topic, and description. Soquacktic, the rubber duck, writes one diagnostic note and can reply once to what the student types. When the student figures it out, they write what changed, and the blocker is kept on their **My blockers** page. The shared databank keeps course notes and those diagnostic notes. If the student is still stuck, they choose a meeting method, response time, session length, and bounty, and post for a peer tutor on this platform. Nothing in that flow stops them from getting help from a tutor somewhere else.

Administrators approve tutors and keep accounts working. That management is how the platform runs. It is not the innovation.

## Tech stack

- **Backend:** Java 21, Spring Boot 4.1.1 (Spring Web, Spring Security, Spring Data JPA)
- **Database:** Embedded H2 (file-based)
- **Frontend:** Thymeleaf, Bootstrap 5 served from `src/main/resources/static/vendor`, and `css/app.css`

## Prerequisites

- **JDK 21** installed and on your `PATH`
- A global Maven install is **not** required; this repo includes the Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Running locally

1. Clone or extract the repository.
2. In a terminal, go to the project root (the folder that contains `pom.xml`).
3. Start the app:
   - **Windows:** `mvnw.cmd spring-boot:run`
   - **macOS / Linux:** `./mvnw spring-boot:run`
4. On a **fresh** database, the console prints `Knowledge bank loaded successfully` when course notes are inserted. It prints `Local demo accounts loaded successfully.` only when `application-local.properties` enables those accounts and the users table is empty. If `data/` already has those rows, those lines do not appear.

Leave port 8080 free. A second start while the first process is using `data/` exits with `LearnIT is already running. Stop the other copy before starting this one.` On macOS or Linux, `./mvnw` has to be executable.

Run the tests from that same folder: `mvnw.cmd test` on Windows, or `./mvnw test` on macOS or Linux.

A diagnostic note is required before a request exists. **Name a blocker** on the student home opens Talk it through, which stores that note. The app uses the offline note when `groq.api.key` is blank, `mock-key`, or unset, and when a live reply cannot be shown. To use a live note, create a free key at [console.groq.com](https://console.groq.com) and set `groq.api.key` in `application-local.properties`. You can instead set `GROQ_API_KEY` in the environment, as long as that local file does not set `groq.api.key` itself. The key is not stored in git.

## Accessing the application

- **Login:** http://localhost:8080/login
- **Register (new student accounts):** http://localhost:8080/register
- **H2 console:** http://localhost:8080/h2-console  
  JDBC URL: `jdbc:h2:file:./data/rapidtutor`  
  Username: `sa`  
  Password: (leave blank)

Public registration creates **student** accounts. Tutors apply at http://localhost:8080/apply/tutor, and an administrator approves them before they can offer a time. That approval is platform management. An approved applicant signs out and signs in again to open the tutor board. A session is claimed when the student accepts that time.

Optional accounts for your own machine are read from `application-local.properties` when `rapidtutor.demo-users.enabled` is true. Copy `application-local.properties.example` and set a distinct username and a password of at least 10 characters for the student, tutor, and admin. Do not use the password `password`. That file is gitignored. Those accounts are inserted only when every value is set and the users table is empty. The local student and tutor start with 100 tokens, and that tutor is given every offered course. The local admin starts with 0 tokens. A fresh clone without that file has no pre-created logins.

## Architecture and database notes

- H2 writes to `./data/rapidtutor`. That folder is gitignored so each machine gets its own database.
- The knowledge bank is seeded independently of users. Missing course notes are still inserted when the users table already has rows.
- To reset everything (users, tokens, blockers, requests, knowledge bank): stop the server, delete the `data/` folder, and start the app again. Local demo accounts are created again only when `application-local.properties` enables them.
- On startup, an older database gets wider columns for request descriptions (2000 characters) and review feedback (1000 characters), and a `VERSION` column on `USERS` when that column is missing. You do not need to delete `data/` for those updates.
