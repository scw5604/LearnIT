# LearnIT

LearnIT is a proof-of-concept for the IST 440W capstone. A student tries to clear a course blocker before a person is involved. Soquacktic, the rubber duck, writes one diagnostic note and can reply once to what the student types. The shared databank keeps course notes and those diagnostic notes. If the student is still stuck, they can post for a peer tutor on this platform. Nothing in that flow stops them from getting help from a tutor somewhere else.

Administrators approve tutors and keep accounts working. That management is how the platform runs. It is not the innovation.

## Tech stack

- **Backend:** Java 21, Spring Boot 4.1.1 (Spring Web, Spring Security, Spring Data JPA)
- **Database:** Embedded H2 (file-based)
- **Frontend:** Thymeleaf, Bootstrap 5 (CDN)

## Prerequisites

- **JDK 21** installed and on your `PATH`
- A global Maven install is **not** required; this repo includes the Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Running locally

1. Clone or extract the repository.
2. In a terminal, go to the project root (the folder that contains `pom.xml`).
3. Start the app:
   - **Windows:** `mvnw.cmd spring-boot:run`
   - **macOS / Linux:** `./mvnw spring-boot:run`
4. On a **fresh** database, the console prints when demo users and the knowledge bank are seeded. If you already have a local `data/` folder from a previous run, those messages will not appear.

A diagnostic note is required before a request exists. Talk it through stores that note. If `GROQ_API_KEY` is unset, or the reply cannot be shown, the app uses the offline note. To use a live note, create a free key at [console.groq.com](https://console.groq.com) and start the app with that variable set. The key is not stored in the project.

## Accessing the application

- **Login:** http://localhost:8080/login
- **Register (new student accounts):** http://localhost:8080/register
- **H2 console:** http://localhost:8080/h2-console  
  JDBC URL: `jdbc:h2:file:./data/rapidtutor`  
  Username: `sa`  
  Password: (leave blank)

Public registration creates **student** accounts. Tutors apply at http://localhost:8080/apply/tutor, and an administrator approves them before they can offer a time. That approval is platform management. A session is claimed when the student accepts that time. Seeded `tutor1` can offer times immediately.

## Default test accounts

`DataSeeder` inserts these users when the `users` table is empty:

Passwords are stored with BCrypt. If you already have a `data/` folder from an older run, stop the app, delete that folder, and start again so these accounts are reseeded.

| Role | Username | Password | Use |
|---|---|---|---|
| Student | `student1` | `password` | Talk it through, use the databank, or post a request |
| Tutor | `tutor1` | `password` | Offer times for HCDD 412, IST 440W, IST 311, HCDD 411, HCDD 331, and IST 230 |
| Admin | `admin` | `password` | Approve tutors, overview, and insights |

## Architecture and database notes

- H2 writes to `./data/rapidtutor`. That folder is gitignored so each machine gets its own empty database and seeder run.
- The knowledge bank is seeded independently of users. If demo accounts already exist but articles do not, articles are still loaded.
- To reset everything (users, tokens, tickets, knowledge bank): stop the server, delete the `data/` folder, and start the app again.
