# RapidTutor

RapidTutor is a proof-of-concept Java web application for time-sensitive, on-demand peer-tutoring requests. Built as a monolithic MVC app for the IST 440W capstone, it connects students who have a localized academic blocker with available peer tutors.

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

Diagnostic notes are optional. If `GROQ_API_KEY` is unset, diagnosis falls back to an offline demo message. To use a live note, create a free key at [console.groq.com](https://console.groq.com) and start the app with that variable set. The key is not stored in the project.

## Accessing the application

- **Login:** http://localhost:8080/login
- **Register (new student accounts):** http://localhost:8080/register
- **H2 console:** http://localhost:8080/h2-console  
  JDBC URL: `jdbc:h2:file:./data/rapidtutor`  
  Username: `sa`  
  Password: (leave blank)

Public registration creates **student** accounts only. Use the seeded tutor account below to claim requests.

## Default test accounts

`DataSeeder` inserts these users when the `users` table is empty:

Passwords are stored with BCrypt. If you already have a `data/` folder from an older run, stop the app, delete that folder, and start again so these accounts are reseeded.

| Role | Username | Password | Use |
|---|---|---|---|
| Student | `student1` | `password` | Post requests, cancel, resolve, review |
| Tutor | `tutor1` | `password` | Board for HCDD 412 and IST 440W |
| Admin | `admin` | `password` | Overview and learning insights |

## Architecture and database notes

- H2 writes to `./data/rapidtutor`. That folder is gitignored so each machine gets its own empty database and seeder run.
- The knowledge bank is seeded independently of users. If demo accounts already exist but articles do not, articles are still loaded.
- To reset everything (users, tokens, tickets, knowledge bank): stop the server, delete the `data/` folder, and start the app again.
