package com.rapidtutor.bootstrap;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import com.rapidtutor.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, KnowledgeArticleRepository knowledgeArticleRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        seedDemoUsers();
        seedKnowledgeBank();
    }

    private void seedDemoUsers() {
        String tutorSubjects = String.join(", ", Courses.OFFERED);
        if (userRepository.count() > 0) {
            backfillDemo("student1", "student1@rapidtutor.local");
            backfillDemo("tutor1", "tutor1@rapidtutor.local");
            backfillDemo("admin", "admin@rapidtutor.local");
            User tutor = userRepository.findByUsername("tutor1");
            if (tutor != null && !tutorSubjects.equals(tutor.getSubjects())) {
                tutor.setSubjects(tutorSubjects);
                userRepository.save(tutor);
            }
            return;
        }

        userRepository.save(demoUser("student1", "student1@rapidtutor.local", "STUDENT", null, 100));
        userRepository.save(demoUser("tutor1", "tutor1@rapidtutor.local", "TUTOR", tutorSubjects, 100));
        userRepository.save(demoUser("admin", "admin@rapidtutor.local", "ADMIN", null, 0));

        System.out.println("Synthetic test accounts loaded successfully.");
    }

    private void backfillDemo(String username, String email) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return;
        }
        boolean changed = false;
        if (user.getEmail() == null) {
            user.setEmail(email);
            changed = true;
        }
        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            user.setDisplayName(username);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
    }

    private User demoUser(String username, String email, String role, String subjects, int tokenBalance) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(role);
        user.setSubjects(subjects);
        user.setTokenBalance(tokenBalance);
        return user;
    }

    /** Inserts an article only when that course and topic are not already stored. */
    private void seedKnowledgeBank() {
        int added = seedMissing(hcdd412Articles());
        added += seedMissing(ist440wArticles());
        added += seedMissing(ist311Articles());
        added += seedMissing(hcdd411Articles());
        added += seedMissing(hcdd331Articles());
        added += seedMissing(ist230Articles());
        if (added > 0) {
            System.out.println("Knowledge bank loaded successfully (" + added + " articles).");
        }
    }

    private int seedMissing(List<KnowledgeArticle> articles) {
        int added = 0;
        for (KnowledgeArticle article : articles) {
            if (knowledgeArticleRepository.countByCourseAndTopic(article.getCourse(), article.getTopic()) > 0) {
                continue;
            }
            knowledgeArticleRepository.save(article);
            added++;
        }
        return added;
    }

    private List<KnowledgeArticle> hcdd412Articles() {
        return List.of(
                        new KnowledgeArticle("HCDD 412", "Software Specification - Use Case vs. User Story",
                                "I am writing the requirements document for our complex system and keep confusing Use Cases with User Stories. What is the difference for our assignment?",
                                "Concept: They differ in formality and perspective. Breakdown: 1) User Stories are short, informal descriptions from the end-user's perspective (e.g., 'As a user, I want...'). 2) Use Cases are formal, structured documents detailing the exact step-by-step interactions between the system and external actors to achieve a specific goal."),
                        new KnowledgeArticle("HCDD 412", "System Specification - Non-Functional Requirements",
                                "Our requirements doc needs 5 non-functional requirements. I wrote 'The system should email the user a receipt.' Is that correct?",
                                "Concept: Functional vs. Non-Functional. Breakdown: 1) Emailing a receipt is a Functional Requirement because it defines a specific behavior or action the system performs. 2) Non-Functional Requirements specify how the system operates (e.g., 'The system must load the dashboard in under 2 seconds,' or 'The database must encrypt passwords using AES-256')."),
                        new KnowledgeArticle("HCDD 412", "Software Specification - UML Sequence Diagrams",
                                "In my sequence diagram, how do I represent a loop where the system checks for a user's permission multiple times?",
                                "Concept: Representing iteration in UML. Breakdown: Use a UML 'Combined Fragment' box. Draw a rectangle around the messages that repeat, label the operator box in the top-left corner as loop, and include the loop condition (e.g., [while unauthorized]) inside the frame."),
                        new KnowledgeArticle("HCDD 412", "Architecture - Monolithic vs. Microservices",
                                "Our team project requires us to justify our architectural choice. We are building a simple CRUD application. Is it better to use microservices to get higher grades?",
                                "Concept: Architectural overhead. Breakdown: For a simple CRUD application with a small team, a monolithic architecture is almost always better due to lower deployment overhead and simpler data consistency. Microservices introduce complex network routing and distributed data management that are unnecessary for a small scope."),
                        new KnowledgeArticle("HCDD 412", "Software Design - MVC Architecture",
                                "I have all my database SQL queries directly inside my UI view code. My professor said this violates MVC. How do I fix this?",
                                "Concept: Separation of concerns. Breakdown: Move your SQL queries into a Model (or Repository/DAO) class. Your View should only handle HTML/UI representation, and your Controller should act as the middleman—receiving UI input, calling the Model for data, and passing that data back to the View."),
                        new KnowledgeArticle("HCDD 412", "Design - Coupling and Cohesion",
                                "My professor told me my system has 'high coupling and low cohesion.' What does that mean and how do I fix it?",
                                "Concept: Module independence and focus. Breakdown: 1) 'High coupling' means your classes rely too heavily on each other's internal details. 2) 'Low cohesion' means a single class is doing many unrelated things. Fix this by ensuring each class has only one distinct responsibility (high cohesion) and interacts only through well-defined public interfaces (low coupling)."),
                        new KnowledgeArticle("HCDD 412", "Design Patterns - Singleton Implementation",
                                "I'm trying to implement a database connection manager, but every time I call the class, it creates a new connection. How do I restrict it to just one instance?",
                                "Concept: The Singleton design pattern restricts object creation to a single instance. Breakdown: 1) Make your constructor private so external classes cannot instantiate it. 2) Create a private static instance of the class inside itself. 3) Provide a public static getInstance() method that returns the instance, initializing it only if it is currently null."),
                        new KnowledgeArticle("HCDD 412", "Design Patterns - Factory Method",
                                "My code has a huge if-else block to instantiate different types of Report objects based on a string input. It feels really messy and violates the Open-Closed Principle.",
                                "Concept: Abstracting object creation. Breakdown: Implement the Factory Method design pattern. Create a ReportFactory class with a method that takes the string input and returns the correct subclass of Report. This isolates the instantiation logic and makes it easier to add new report types later."),
                        new KnowledgeArticle("HCDD 412", "Design Patterns - Observer Pattern",
                                "I am building a dashboard where three different charts need to update automatically whenever the underlying dataset changes. Do I need to write a loop to constantly check for changes?",
                                "Concept: Event-driven updates via the Observer pattern. Breakdown: Do not use a polling loop. Make your dataset the 'Subject' and the charts the 'Observers'. When the data changes, the dataset calls a notifyObservers() method, which actively pushes the update to all registered charts simultaneously."),
                        new KnowledgeArticle("HCDD 412", "Testing - Test-Driven Development (TDD) Workflow",
                                "The assignment requires us to use TDD, but I don't understand the workflow. Do I write all my tests for the whole project first?",
                                "Concept: Incremental Red-Green-Refactor cycling. Breakdown: No, TDD is done in micro-cycles for individual features. 1) Write one small test that fails (Red). 2) Write the minimum amount of code to make it pass (Green). 3) Clean up the code (Refactor). Repeat incrementally."),
                        new KnowledgeArticle("HCDD 412", "Testing - JUnit Mocking with Mockito",
                                "I am writing unit tests for a service that calls an external API. I keep getting timeout errors when I run the tests. How do I test my logic without actually hitting the API?",
                                "Concept: Dependency isolation. Breakdown: You should mock the external API dependency using a framework like Mockito. Inject a mock object into your service and use when().thenReturn() to simulate the API's response, allowing you to test your internal logic safely."),
                        new KnowledgeArticle("HCDD 412", "Testing - Integration vs. Unit Testing",
                                "I wrote tests that verify if my Service class correctly saves data to the actual H2 database. Is this considered a unit test?",
                                "Concept: Testing scope boundaries. Breakdown: No, that is an integration test. A unit test evaluates a single class in complete isolation, usually by mocking the database. An integration test verifies that multiple real components (like your Service and the Database) work correctly together."),
                        new KnowledgeArticle("HCDD 412", "Integration - CI/CD Pipeline Triggers",
                                "We want our tests to run automatically every time someone pushes to the GitHub repository, but I don't know how to connect it.",
                                "Concept: Continuous Integration automation. Breakdown: Set up a GitHub Actions workflow. Create a .github/workflows/main.yml file in your repository. Configure it to trigger on push and pull_request events, set up the JDK, and execute your build command (like mvn test or gradle test)."),
                        new KnowledgeArticle("HCDD 412", "Integration - RESTful API 404 Status Codes",
                                "When a user searches for an ID that doesn't exist in my API, I am returning a blank page with a 200 OK status. Is this the best practice?",
                                "Concept: Semantic HTTP status routing. Breakdown: No. For a REST API, if a requested resource is not found, you should return an HTTP 404 Not Found status code. A 200 OK strictly indicates the resource was successfully found and returned."),
                        new KnowledgeArticle("HCDD 412", "Integration - Resolving Git Merge Conflicts",
                                "My teammate and I both edited the same Java file on different branches. When I try to merge my branch into main, Git gives me a merge conflict error.",
                                "Concept: Manual version reconciliation. Breakdown: Git places conflict markers (<<<<<<<, =======, >>>>>>>) inside the affected file. Open the file, review both sets of changes, manually edit the code to the correct final version, remove the markers, and then commit the resolved file."),
                        new KnowledgeArticle("HCDD 412", "Version Control - Reverting vs. Resetting Commits",
                                "I accidentally committed and pushed a file containing my API keys to the main branch. How do I fix this?",
                                "Concept: Safe history modification. Breakdown: First, invalidate/rotate your API keys immediately on the provider's website. Then, use git revert <commit-hash> to create a new commit that undoes the changes. Do not use git reset on a public branch, as it rewrites shared history and will cause conflicts for your teammates."),
                        new KnowledgeArticle("HCDD 412", "Software Evolution - Refactoring 'Long Method' Smells",
                                "One of my methods is over 200 lines long and handles validation, data transformation, and database saving. It works, but how do I refactor it?",
                                "Concept: The Single Responsibility Principle. Breakdown: This is a 'Long Method' code smell. Use the 'Extract Method' refactoring technique to break it down into three separate, private helper methods: validateInput(), transformData(), and saveToDatabase()."),
                        new KnowledgeArticle("HCDD 412", "Agile Methodologies - Scrum Master Role",
                                "We are setting up our team roles for the project. Is the Scrum Master basically just the Project Manager who assigns all the coding tasks?",
                                "Concept: Servant-leadership in Agile. Breakdown: No. In Agile Scrum, the development team is self-organizing and assigns their own tasks. The Scrum Master acts as a facilitator and coach, ensuring the team follows Scrum practices and helping remove any blockers (impediments) that slow the team down.")
        );
    }

    private List<KnowledgeArticle> ist440wArticles() {
        return List.of(
                new KnowledgeArticle(Courses.IST_440W, "Sprint goal versus a task list",
                        "Our sprint backlog is a long list of tasks. Is that the same thing as a sprint goal?",
                        "Concept: A sprint goal is one outcome the team commits to this sprint. Breakdown: Tasks are the work that serves that outcome. If the list has no shared outcome, write one sentence that says what will be true for the user when the sprint ends, then keep only the tasks that move that sentence forward."),
                new KnowledgeArticle(Courses.IST_440W, "Stakeholder interviews",
                        "I sent a 20-question survey to our sponsor. Do I still need an interview?",
                        "Concept: Interviews uncover goals a survey cannot phrase in advance. Breakdown: Ask what decision the sponsor has to make, what they do today, and what failure looks like. Use the survey later to check how widely those answers apply."),
                new KnowledgeArticle(Courses.IST_440W, "Risk register",
                        "I listed 'the project might be late' as our only risk. What else belongs in the register?",
                        "Concept: A risk is a specific uncertain event, plus how likely it is and how bad it is. Breakdown: Replace the vague line with events such as 'the sponsor is unavailable in week 3' or 'the external API changes.' For each one, note likelihood, impact, and the action you will take if it happens."),
                new KnowledgeArticle(Courses.IST_440W, "Acceptance criteria",
                        "Our story says 'the user can log in.' How do I write acceptance criteria that a teammate can test?",
                        "Concept: Acceptance criteria are observable checks, not a restatement of the story. Breakdown: State the starting condition, the action, and the visible result. Example: given a registered account, when the password matches, the user lands on the dashboard. Include one failure case, such as a wrong password."),
                new KnowledgeArticle(Courses.IST_440W, "Useful peer review comments",
                        "I reviewed a teammate's section and wrote 'looks good' because I did not want to be harsh. What should a review comment say?",
                        "Concept: A useful comment points at a specific spot and a specific gap. Breakdown: Name the section, say what a reader cannot do or understand there, and suggest the change. 'Looks good' does not tell the author what to keep or what to fix."),
                new KnowledgeArticle(Courses.IST_440W, "Options analysis - weighing alternative solutions",
                        "Our sponsor asked us to pick a solution. I listed one idea and wrote why we like it. Is that an options analysis?",
                        "Concept: An options analysis compares more than one real alternative against the same criteria. Breakdown: Name at least two options the organization could actually pursue. Use the same criteria for each, such as cost, time, risk, and fit with the people who will use it. Say which option you recommend and which tradeoff you are accepting."),
                new KnowledgeArticle(Courses.IST_440W, "Project management - work plan versus a wish list",
                        "Our work plan is a list of things we hope to finish by the end of the semester. The instructor said that is not a plan. What is missing?",
                        "Concept: A work plan ties each task to an owner, a date, and a deliverable. Breakdown: For every item, write who will do it, when it is due, and what finished looks like. Drop items that have no owner. A wish list says what the team wants. A work plan says how the next few weeks will produce it."),
                new KnowledgeArticle(Courses.IST_440W, "Communication - writing for a sponsor",
                        "I pasted our team chat into the sponsor update because it already explains what we did. Why did the instructor ask me to rewrite it?",
                        "Concept: A sponsor needs a decision and a status, not the team's internal conversation. Breakdown: Open with what you need from the sponsor, or what changed since the last update. Then give the result, the risk, and the next step in plain language. Leave the debate, the jokes, and the unfinished options in the team notes.")
        );
    }

    private List<KnowledgeArticle> ist311Articles() {
        return List.of(
                new KnowledgeArticle(Courses.IST_311, "Objects - class versus object",
                        "I wrote one Customer class and my program only lets me store one customer. I thought the class was the customer.",
                        "Concept: A class is the blueprint. An object is one thing built from that blueprint. Breakdown: The Customer class describes the fields and methods every customer has. Each new Customer() call creates a separate object with its own name and balance. To store many customers, keep many objects, usually in a list, all built from that one class."),
                new KnowledgeArticle(Courses.IST_311, "Encapsulation - hiding fields",
                        "My fields are public so the form can set them directly. The rubric says that breaks encapsulation. What should I change?",
                        "Concept: Encapsulation keeps an object's data behind methods the class controls. Breakdown: Make the fields private. Add methods that check the new value before storing it, such as setEmail rejecting a blank address. Other classes call those methods. They do not assign the field themselves."),
                new KnowledgeArticle(Courses.IST_311, "Design - inheritance versus composition",
                        "A Car is a Vehicle, so I also made Engine extend Car. The instructor said Engine should not be a subclass. Why?",
                        "Concept: Inheritance means 'is a kind of.' Composition means 'has a.' Breakdown: A Car is a kind of Vehicle, so Car can extend Vehicle. A Car has an Engine, so Car should hold an Engine field. Extending Car from Engine claims an engine is a kind of car, which is not the relationship you mean."),
                new KnowledgeArticle(Courses.IST_311, "Polymorphism - one method, many types",
                        "I have Dog and Cat classes and a long if-else that calls bark or meow. How do I let a list of animals each make their own sound?",
                        "Concept: Polymorphism lets each subclass supply its own version of a shared method. Breakdown: Give Animal a method such as speak(). Dog and Cat each override speak() with their own behavior. Your loop holds Animal references and calls speak() on each one. The object decides which version runs. You do not add a new branch for every animal."),
                new KnowledgeArticle(Courses.IST_311, "Requirements - writing a use case",
                        "I wrote 'the user uses the system' as our use case. That seems too short, but I do not know what else belongs in it.",
                        "Concept: A use case is one goal an actor completes with the system, told as steps. Breakdown: Name the actor and the goal, such as 'student drops a course.' List the main steps from the actor's action to the system's response. Add what happens when a step fails, such as the drop deadline has passed. 'Uses the system' is not a goal."),
                new KnowledgeArticle(Courses.IST_311, "Class diagrams - choosing a relationship",
                        "My class diagram connects Student and Course with a plain line. I do not know whether that should be association, aggregation, or inheritance.",
                        "Concept: The line should name the real relationship and how many of each side take part. Breakdown: A student enrolls in many courses, and a course has many students, so draw an association and label both ends with the counts. Use inheritance only when one class is a kind of the other. A plain line does not say which relationship you mean."),
                new KnowledgeArticle(Courses.IST_311, "CRC cards - finding responsibilities",
                        "I filled a CRC card with every field in the database table. The instructor said those are not responsibilities. What goes on the card?",
                        "Concept: A CRC card records a class name, what it is responsible for doing, and which other classes it collaborates with. Breakdown: Write duties such as 'checks that a seat is open' or 'records the enrollment.' Collaborators are the other classes it must talk to, such as Course. Columns from a table are data, not the jobs the class performs.")
        );
    }

    private List<KnowledgeArticle> hcdd411Articles() {
        return List.of(
                new KnowledgeArticle(Courses.HCDD_411, "Architecture - client versus server",
                        "I put the whole application in one program and called the part that shows the form the server. Is that a client and a server?",
                        "Concept: A client asks for a service. A server provides that service to clients over a network. Breakdown: The client is the program a person uses. The server is the program that waits for requests, does the shared work, and sends a response. Two methods in one program are not a client and a server until they run as separate programs and talk over the network."),
                new KnowledgeArticle(Courses.HCDD_411, "Remote calls - remote versus local",
                        "I call getBalance() the same way I call a method in my own class. The instructor said a remote call can fail even when my code is correct. Why?",
                        "Concept: A local call stays inside one running program. A remote call sends the request to another program and waits for a reply. Breakdown: The other program may be down, busy, or unreachable. Your code needs a result for that case, such as showing an error instead of assuming getBalance() always returns. A local call does not cross that boundary."),
                new KnowledgeArticle(Courses.HCDD_411, "Distribution - sending an object",
                        "I tried to send a Student object to the server and the call failed because Student is not serializable. What does the server actually receive?",
                        "Concept: A remote call sends a copy of the data, not the object living in your program's memory. Breakdown: Mark the class so its fields can be turned into bytes, send those bytes, and let the server rebuild an object from them. The server does not share your original object. Fields that cannot travel, such as an open file, need to be left out."),
                new KnowledgeArticle(Courses.HCDD_411, "Deployment - where each part runs",
                        "Our diagram shows the client, the application server, and the database all in one box labeled 'the cloud.' How do I show deployment?",
                        "Concept: A deployment description says which program runs on which machine. Breakdown: Draw a separate node for the student's computer, the server that receives requests, and the machine that stores the data. Put each program inside the node where it runs. One box hides which part you can restart, secure, or scale on its own."),
                new KnowledgeArticle(Courses.HCDD_411, "Failure - remote service is down",
                        "When the inventory service is offline, my checkout page spins until the browser gives up. What should the client do instead?",
                        "Concept: A distributed client needs a defined response when a service does not answer. Breakdown: Set a timeout so the page does not wait forever. Tell the user the inventory check failed and what they can do next, such as retry. Leave the cart intact. Do not treat silence from the service as an empty inventory.")
        );
    }

    private List<KnowledgeArticle> hcdd331Articles() {
        return List.of(
                new KnowledgeArticle(Courses.HCDD_331, "Goals - useful versus usable",
                        "Our app has a clean layout, but the class said it is not useful. I thought a clear screen meant we were done.",
                        "Concept: Usable means people can operate the interface. Useful means it helps them accomplish a real goal. Breakdown: A clear screen can still omit the task people came to do. Ask what the user is trying to finish, then check whether the product lets them finish it. A usable control that serves the wrong job does not make the product useful."),
                new KnowledgeArticle(Courses.HCDD_331, "Evaluation - heuristic review",
                        "I asked two friends if they liked the colors. I called that a heuristic evaluation. What is the review actually looking for?",
                        "Concept: A heuristic review checks an interface against known usability principles, not personal taste. Breakdown: Walk through the main tasks and note where the interface hides status, uses inconsistent labels, or gives no way to undo. Record the screen and the principle it breaks. 'I like the blue' is a preference, not a finding."),
                new KnowledgeArticle(Courses.HCDD_331, "Cognition - cognitive load",
                        "The form asks for twelve fields before the user can see any results. People quit. Is the fix a brighter submit button?",
                        "Concept: Cognitive load is the amount of mental work a step demands. Breakdown: A brighter button does not reduce the work of remembering twelve answers. Ask only for what the next step needs, and let people reach a useful result sooner. Move the rest until it is needed."),
                new KnowledgeArticle(Courses.HCDD_331, "Users - writing a persona",
                        "I wrote a persona that says our user is 20, likes technology, and lives on campus. The instructor said that is a demographic, not a persona.",
                        "Concept: A persona is a concrete person with a goal, a context, and a constraint. Breakdown: Give the person a task they are trying to finish, where they are when they do it, and what gets in the way, such as one free hand or ten minutes between classes. Age and a slogan do not tell you what the design must support."),
                new KnowledgeArticle(Courses.HCDD_331, "Testing - writing a usability task",
                        "My test script says 'use the new filter feature and tell us if you like it.' Participants clicked around and said it was fine. Why is that a weak task?",
                        "Concept: A usability task gives a realistic goal and lets you watch whether the person can reach it. Breakdown: Ask them to find a specific result, such as courses that meet after 4 p.m. on Tuesday. Do not name the button they should use. Note where they hesitate and whether they finish. Liking the feature is a separate question.")
        );
    }

    private List<KnowledgeArticle> ist230Articles() {
        return List.of(
                new KnowledgeArticle(Courses.IST_230, "Sets - union, intersection, and complement",
                        "I have two sets of student IDs, A and B. I keep mixing up which operation gives IDs in both, IDs in either, and IDs in neither.",
                        "Concept: Union, intersection, and complement answer different membership questions. Breakdown: A \u222a B is every ID that is in A, in B, or in both. A \u2229 B is only the IDs that appear in both. The complement of A is every ID in the universe you defined that is not in A. Write the universe first, or the complement is not defined."),
                new KnowledgeArticle(Courses.IST_230, "Logic - predicates and quantifiers",
                        "I wrote 'all students passed some exam' and my instructor said the order of 'all' and 'some' changes the claim. How?",
                        "Concept: A quantifier says how many things a statement covers, and the order matters. Breakdown: 'For every student, there is an exam they passed' allows a different exam for each student. 'There is an exam that every student passed' means one shared exam. Write the 'for all' or 'there exists' in the order you mean, then the condition."),
                new KnowledgeArticle(Courses.IST_230, "Logic - reading a truth table",
                        "I built a truth table for P AND Q, but the rows do not match the definition. I only listed the row where both are true.",
                        "Concept: A truth table lists every combination of truth values, not only the row you hope is true. Breakdown: Two statements need four rows: true/true, true/false, false/true, and false/false. AND is true only in the first of those rows. OR is true in every row except false/false. Fill every row before you decide what the expression means."),
                new KnowledgeArticle(Courses.IST_230, "Counting - permutation versus combination",
                        "I need the number of ways to choose 3 committee members from 10 students. I used a permutation and got a much larger number. Which one counts a committee?",
                        "Concept: A permutation counts order. A combination does not. Breakdown: If assigning president, secretary, and treasurer, order matters, so use a permutation. A committee of three has the same members no matter who is listed first, so use a combination. The larger number is counting each committee several times, once for every way to order the same three people."),
                new KnowledgeArticle(Courses.IST_230, "Graphs - when to use a tree",
                        "I drew a diagram of prerequisites and called it a tree. Two courses both lead to the same later course, and the instructor said it is a graph but not a tree.",
                        "Concept: A tree is a connected graph with no cycles, so each node has one path back to the root. Breakdown: If two arrows arrive at the same course from different prerequisites, there are two ways to reach it. That is still a graph, and it is the right model. It is not a tree. Use a tree only when every node has exactly one parent, except the root."),
                new KnowledgeArticle(Courses.IST_230, "Proof - starting an induction",
                        "The assignment says to prove a statement for every positive integer by induction. I checked n = 1, n = 2, and n = 3. Is that the proof?",
                        "Concept: Induction proves the base case, then proves that any one case forces the next. Breakdown: Show the statement is true for the starting value, often n = 1. Then assume it is true for some k and use that assumption to show it is true for k + 1. Three examples support a hunch. They do not cover every later integer."),
                new KnowledgeArticle(Courses.IST_230, "Languages - reading a finite-state machine",
                        "I have a state diagram with circles and labeled arrows. I do not know how to tell whether it accepts the string 101.",
                        "Concept: A finite-state machine accepts a string when the symbols lead from the start state to an accept state. Breakdown: Begin at the start state. For each symbol, follow the one arrow labeled with that symbol. After the last symbol, look at the state you landed in. If it is marked as an accept state, the machine accepts 101. If you have no arrow for the next symbol, it rejects the string.")
        );
    }
}
