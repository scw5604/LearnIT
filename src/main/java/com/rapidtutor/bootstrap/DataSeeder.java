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
        if (userRepository.count() > 0) {
            return;
        }

        userRepository.save(demoUser("student1", "STUDENT", null, 100));
        userRepository.save(demoUser("tutor1", "TUTOR", Courses.HCDD_412 + ", " + Courses.IST_440W, 100));
        userRepository.save(demoUser("admin", "ADMIN", null, 0));

        System.out.println("Synthetic test accounts loaded successfully.");
    }

    private User demoUser(String username, String role, String subjects, int tokenBalance) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(role);
        user.setSubjects(subjects);
        user.setTokenBalance(tokenBalance);
        return user;
    }

    /** Loads each course independently so an existing bank still receives a newly added course. */
    private void seedKnowledgeBank() {
        int added = seedCourseIfEmpty(Courses.HCDD_412, hcdd412Articles());
        added += seedCourseIfEmpty(Courses.IST_440W, ist440wArticles());
        if (added > 0) {
            System.out.println("Knowledge bank loaded successfully (" + added + " articles).");
        }
    }

    private int seedCourseIfEmpty(String course, List<KnowledgeArticle> articles) {
        if (knowledgeArticleRepository.countByCourse(course) > 0) {
            return 0;
        }
        knowledgeArticleRepository.saveAll(articles);
        return articles.size();
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
                        "Concept: A useful comment points at a specific spot and a specific gap. Breakdown: Name the section, say what a reader cannot do or understand there, and suggest the change. 'Looks good' does not tell the author what to keep or what to fix.")
        );
    }
}
