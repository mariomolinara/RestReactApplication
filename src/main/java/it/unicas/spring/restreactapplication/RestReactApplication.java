package it.unicas.spring.restreactapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the RestReact Spring Boot application.
 *
 * ── ROLE IN THE APPLICATION ───────────────────────────────────────────────────
 * This is the single class that starts the entire application. Running its
 * main() method launches an embedded Tomcat web server, initialises the Spring
 * ApplicationContext (IoC container), connects to PostgreSQL, and begins
 * listening for HTTP requests — all with a single line of code.
 *
 * ── WHAT @SpringBootApplication DOES ─────────────────────────────────────────
 * @SpringBootApplication is a convenience annotation that combines three others:
 *
 *   @SpringBootConfiguration
 *     Marks this class as a source of bean definitions (equivalent to
 *     @Configuration). Spring reads it as part of context initialisation.
 *
 *   @EnableAutoConfiguration
 *     Tells Spring Boot to automatically configure the application based on
 *     the libraries present on the classpath. For example:
 *       - spring-boot-starter-web      → configures DispatcherServlet, Jackson, Tomcat
 *       - spring-boot-starter-data-jpa → configures DataSource, EntityManagerFactory,
 *                                        transaction management, Hibernate
 *       - postgresql driver            → configures the PostgreSQL JDBC connection
 *     Without auto-configuration, all of this would require hundreds of lines of
 *     manual XML or Java configuration.
 *
 *   @ComponentScan
 *     Instructs Spring to scan this package (and all sub-packages) for classes
 *     annotated with @Component, @Service, @Repository, @RestController, etc.
 *     and register them as beans in the ApplicationContext. This is how Spring
 *     discovers FriendController, FriendRepository (via Spring Data), and WebConfig.
 *
 * ── HOW SpringApplication.run() WORKS ────────────────────────────────────────
 * SpringApplication.run(RestReactApplication.class, args) performs these steps:
 *
 *   1. Creates the ApplicationContext (the IoC container).
 *   2. Runs @ComponentScan to find and register all beans.
 *   3. Applies auto-configuration (DataSource, JPA, web MVC, …).
 *   4. Wires dependencies between beans (DI — constructor injection, etc.).
 *   5. Starts the embedded Tomcat server on port 8080 (default).
 *   6. The application is now ready to handle HTTP requests.
 *
 * ── RELATION TO IoC/DI ────────────────────────────────────────────────────────
 * This class is the bootstrap point for the entire IoC container. Once run()
 * returns, Spring has already:
 *   - Instantiated FriendController and injected FriendRepository into it.
 *   - Registered the generated FriendRepository proxy as a singleton bean.
 *   - Applied CORS configuration via WebConfig.
 * All of this happens automatically — no "new" is called by application code.
 */
@SpringBootApplication
public class RestReactApplication {

    /**
     * Standard Java entry point.
     *
     * Delegates immediately to SpringApplication.run(), which bootstraps the
     * entire Spring ecosystem. The 'args' array is passed through so that
     * Spring Boot command-line properties (e.g. --server.port=9090) work
     * correctly when the application is started from the command line or
     * a Docker container.
     *
     * @param args command-line arguments forwarded to Spring Boot.
     */
    public static void main(String[] args) {
        SpringApplication.run(RestReactApplication.class, args);
    }
}
