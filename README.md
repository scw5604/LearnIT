# LearnIT

LearnIT is a proof-of-concept for the IST 440W capstone. A student tries to clear a course problem before a person is involved. They name what they are stuck on with a course, topic, and description. Soquacktic, the rubber duck, writes one diagnostic note and can reply once to what the student types. When the student figures it out, they write what changed, and the attempt is kept on their **What I'm stuck on** page. The shared databank keeps course notes and the first diagnostic note written for each course and topic. If the student is still stuck, they choose a meeting method, response time, session length, and bounty, and post for a peer tutor on this platform.

Administrators approve tutor applications and manage accounts.

## Tech stack

- **Backend:** Java 21, Spring Boot 4.1.1 (Spring Web, Spring Security, Spring Data JPA)
- **Database:** Embedded H2 (file-based)
- **Frontend:** Thymeleaf, Bootstrap 5 served from `src/main/resources/static/vendor`, and `css/app.css`
- **Diagnostic note:** Groq chat completions API (model `openai/gpt-oss-20b`), with an offline fallback

LearnIT was developed under the working name RapidTutor, so packages, properties, and the database file use `rapidtutor`.

## Prerequisites

- **JDK 21 or newer**, with `java` on your `PATH` (check with `java -version`). The recorded demo ran on JDK 25.0.1.
- **Internet access.** On first run, the Maven Wrapper downloads Maven 3.9.16 and all dependencies, which takes a few minutes. The live diagnostic note also calls `api.groq.com`.
- **A Groq API key.** Create a free key at [console.groq.com](https://console.groq.com).
- A global Maven install is **not** required; this repo includes the Maven Wrapper (`mvnw` / `mvnw.cmd`).
- Port **8080** must be free.

## Setup (required)

1. Clone the repository and open a terminal in the folder that contains `pom.xml`:
   ```
   git clone https://github.com/scw5604/LearnIT.git
   cd LearnIT
   ```
2. Create the local config file. Without it there is no admin account, so tutors cannot be approved, and the diagnostic note uses the offline text.
   - **Windows (PowerShell):** `Copy-Item application-local.properties.example application-local.properties`
   - **Windows (Command Prompt):** `copy application-local.properties.example application-local.properties`
   - **macOS / Linux:** `cp application-local.properties.example application-local.properties`
3. Edit `application-local.properties` and set:
   ```
   groq.api.key=<your Groq API key>
   rapidtutor.demo-users.enabled=true
   rapidtutor.demo-users.student.username=demoStudent
   rapidtutor.demo-users.student.password=<choose one, 10+ characters>
   rapidtutor.demo-users.tutor.username=demoTutor
   rapidtutor.demo-users.tutor.password=<choose one, 10+ characters>
   rapidtutor.demo-users.admin.username=demoAdmin
   rapidtutor.demo-users.admin.password=<choose one, 10+ characters>
   ```
   Each password must be at least 10 characters, must not be `password`, and must not match its username. This file is gitignored, so the key and passwords stay on your machine. You can instead set the key in a `GROQ_API_KEY` environment variable, as long as this file does not set `groq.api.key` itself.
4. **macOS / Linux only:** `chmod +x mvnw`

## Running

Start the app from the project folder. Run it from a terminal in that folder, because `application-local.properties` is read relative to the folder the app starts in.
- **Windows (PowerShell):** `.\mvnw.cmd spring-boot:run`
- **Windows (Command Prompt):** `mvnw.cmd spring-boot:run`
- **macOS / Linux:** `./mvnw spring-boot:run`

On a first run with an empty database, the console ends with:
```
Started RapidtutorApplication in ... seconds
Local demo accounts loaded successfully.
Databank loaded successfully (50 articles).
```
If `Local demo accounts loaded successfully.` does not appear, check step 3, then stop the app, delete the `data/` folder, and start it again. If a username or password in step 3 is missing or too weak, the console prints `Demo user seeding skipped. Set a distinct username and a password of at least 10 characters for each local account.` instead. On later runs with the same `data/` folder, those two lines do not appear because the rows already exist.

To stop the app, press **Ctrl+C** in the terminal where it is running. On Windows, answer `Y` if asked `Terminate batch job (Y/N)?`.

A second start while the first process is using `data/` exits with `LearnIT is already running. Stop the other copy before starting this one.`

Open http://localhost:8080/login and sign in:
- `demoStudent` opens **What I'm stuck on** (`/student/dashboard`)
- `demoTutor` opens the **tutor board** (`/tutor/board`)
- `demoAdmin` opens the **admin overview** (`/admin`)

The local student and tutor start with 100 tokens, and that tutor is given every offered course. The local admin starts with 0 tokens.

## Running the tests

- **Windows (PowerShell):** `.\mvnw.cmd test`
- **Windows (Command Prompt):** `mvnw.cmd test`
- **macOS / Linux:** `./mvnw test`

Expected result: `Tests run: 166, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`. The tests use an in-memory database and a mock key, so they do not need the config file or a Groq key.

## The diagnostic note

A diagnostic note is required before a request exists. **I'm stuck** on the student home opens Talk it through, which stores that note. The topic field shows an example from that course's databank note. Typing the example shows the note before a new one is written.

The recorded demo used a live Groq key. The note is written by the model, so its wording changes on every run and will not match the recording word for word. It always has three labeled lines: **Core concept**, **Diagnostic question**, and **Starting step**.

If the key is missing, invalid, or the call fails, the app shows the offline note instead. The offline note looks like this, for a topic such as "Observer pattern":
```
Core concept: Observer pattern is the idea to clarify before the session.
Diagnostic question: What have you already tried with Observer pattern?
Starting step: Write the part of Observer pattern you can explain in one sentence.
```
The app also uses the offline note when a live reply fails its own check, for example when the reply is missing a section or contains code or a final answer. If you see that wording once, choose **Start over** on that page and try again. If you see it every time, the live key is not being used. The offline note is never saved to the databank, so choosing **I figured it out** with it shows only `Saved.` Check the console for a line starting with `AI diagnosis failed:`, such as a `401 Unauthorized` for a wrong key.

The app uses the Groq model `openai/gpt-oss-20b`. If Groq retires that model, every call fails and the offline note appears. To use another Groq model, add `groq.model=<model id>` to `application-local.properties`, or set the `GROQ_MODEL` environment variable. Current model ids are listed at [console.groq.com/docs/models](https://console.groq.com/docs/models).

## Accessing the application

- **Login:** http://localhost:8080/login
- **Register (new student accounts):** http://localhost:8080/register
- **Tutor application:** http://localhost:8080/apply/tutor
- **H2 console (admin only):** sign in as `demoAdmin` first, then open http://localhost:8080/h2-console  
  JDBC URL: `jdbc:h2:file:./data/rapidtutor`  
  Username: `sa`  
  Password: (leave blank)

Public registration creates **student** accounts. Tutors apply at http://localhost:8080/apply/tutor, and an administrator approves them before they can offer a time. An approved applicant signs out and signs in again to open the tutor board. A session is claimed when the student accepts that time.

## Architecture and database notes

- H2 writes to `./data/rapidtutor`. That folder is gitignored so each machine gets its own database.
- The databank is seeded independently of users. Missing course notes are still inserted when the users table already has rows.
- To reset everything (users, tokens, stuck attempts, requests, databank): stop the server, delete the `data/` folder, and start the app again. The local accounts are created again from `application-local.properties`.
- On startup, an older database gets wider columns for request descriptions (2000 characters) and review feedback (1000 characters), and a `VERSION` column on `USERS` when that column is missing. You do not need to delete `data/` for those updates.
