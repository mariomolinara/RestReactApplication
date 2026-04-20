package it.unicas.spring.restreactapplication;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the application.
 *
 * ── ROLE IN THE APPLICATION ───────────────────────────────────────────────────
 * This class has one responsibility: configure CORS (Cross-Origin Resource
 * Sharing) so that the React single-page application — which is served from a
 * different origin than the API — is allowed to call the REST endpoints.
 *
 * Without this configuration, the browser would block every AJAX request made
 * by the React front-end to the Spring back-end, because they appear to come
 * from different origins (different port or domain).
 *
 * ── WHAT IS CORS? ─────────────────────────────────────────────────────────────
 * CORS is a browser security mechanism. By default, a web page loaded from
 * origin A (e.g., http://localhost:3000) is not allowed to make HTTP requests
 * to origin B (e.g., http://localhost:8080). This is called the "same-origin
 * policy" and it protects users from malicious cross-site requests.
 *
 * A server can explicitly opt-in to allow cross-origin requests by including
 * special HTTP response headers:
 *
 *   Access-Control-Allow-Origin:  *
 *   Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
 *   Access-Control-Allow-Headers: *
 *
 * The browser reads these headers and, if they match the request, allows the
 * JavaScript code to access the response. If they are absent, the browser
 * silently blocks the response (the request still reaches the server, but the
 * browser hides the response from JavaScript).
 *
 * ── WHAT IS THE PREFLIGHT REQUEST (OPTIONS)? ──────────────────────────────────
 * Before sending a "non-simple" request (e.g., PUT, DELETE, or any request with
 * a Content-Type: application/json header), the browser first sends an automatic
 * OPTIONS request to the same URL. This is called a "preflight" request.
 * The server must respond to OPTIONS with the appropriate CORS headers, otherwise
 * the browser will never send the real request. That is why OPTIONS is included
 * in the list of allowed methods.
 *
 * ── ROLE OF @Configuration ────────────────────────────────────────────────────
 * @Configuration marks this class as a source of Spring bean definitions.
 * Spring's component scan (triggered by @SpringBootApplication) finds it,
 * instantiates it as a singleton bean, and calls addCorsMappings() during the
 * web MVC setup phase. No explicit "new WebConfig()" is ever called — Spring's
 * IoC container manages the lifecycle.
 *
 * ── ROLE OF WebMvcConfigurer ──────────────────────────────────────────────────
 * WebMvcConfigurer is a Spring MVC interface that provides callback methods for
 * customising the web layer without replacing the entire auto-configured MVC
 * setup. By implementing it here and overriding addCorsMappings(), this class
 * plugs the CORS rules into the existing configuration rather than replacing it.
 */
@Configuration  // Registers this class as a configuration bean in the ApplicationContext.
public class WebConfig implements WebMvcConfigurer {

    /**
     * Registers CORS rules that apply to all /api/** endpoints.
     *
     * This method is called automatically by Spring MVC during startup as part of
     * the WebMvcConfigurer callback mechanism — another example of IoC: Spring
     * calls our code at the right time; we do not call Spring.
     *
     * Rules applied:
     *   .addMapping("/api/**")
     *     Apply these CORS rules to every URL that starts with /api/.
     *     The React front-end calls endpoints under /api/v1/friends, so this
     *     pattern covers all REST endpoints in the application.
     *
     *   .allowedOriginPatterns("*")
     *     Accept requests from any origin (any domain / port).
     *     In a production environment this should be restricted to the exact
     *     domain of the front-end (e.g., "https://myapp.example.com") to
     *     prevent unintended cross-origin access from other websites.
     *
     *   .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
     *     Explicitly permit the HTTP methods used by the CRUD API.
     *     OPTIONS must be included to handle browser preflight requests.
     *
     *   .allowedHeaders("*")
     *     Accept any request header. The React fetch() / axios calls include
     *     Content-Type: application/json, which would be blocked without this.
     *
     * @param registry the CORS registry provided by Spring MVC into which
     *                 mappings are registered (injected by the framework, not by us).
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
