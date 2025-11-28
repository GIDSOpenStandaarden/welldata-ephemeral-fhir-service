package nl.gidsopenstandaarden.welldata.fhir.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Interceptor that extracts the Bearer token from the Authorization header
 * and sets up the AccessTokenContext for the current request.
 *
 * This interceptor does NOT validate the token signature - that should be done
 * by the authorization server or a separate validation interceptor.
 * It only decodes the token to extract claims for session scoping.
 */
@Interceptor
public class AccessTokenInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTokenInterceptor.class);

    private final SessionManager sessionManager;
    private Consumer<SessionManager.Session> sessionInitializer;

    public AccessTokenInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Set a callback to initialize new sessions (e.g., load initial data).
     */
    public void setSessionInitializer(Consumer<SessionManager.Session> sessionInitializer) {
        this.sessionInitializer = sessionInitializer;
    }

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public void incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
        String requestURI = request.getRequestURI();

        // Skip authentication for metadata and conformance endpoints
        if (isPublicEndpoint(request)) {
            LOG.debug("Skipping token extraction for public endpoint: {}", requestURI);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization)) {
            LOG.debug("No Authorization header on {}", requestURI);
            throw new AuthenticationException(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        }

        String token = StringUtils.trim(StringUtils.removeStartIgnoreCase(authorization, "Bearer"));
        if (StringUtils.isBlank(token)) {
            LOG.warn("No Bearer token found in Authorization header on {}", requestURI);
            throw new AuthenticationException(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        }

        try {
            // Decode the JWT without verification (validation should be done separately)
            DecodedJWT jwt = JWT.decode(token);

            String tokenId = jwt.getId(); // jti claim
            String subject = jwt.getSubject(); // sub claim
            Instant expiry = jwt.getExpiresAtAsInstant(); // exp claim

            // Use token hash as fallback if jti is not present
            if (StringUtils.isBlank(tokenId)) {
                tokenId = String.valueOf(token.hashCode());
            }

            // Check if token is expired
            if (expiry != null && Instant.now().isAfter(expiry)) {
                LOG.warn("Token expired for subject {} on {}", subject, requestURI);
                throw new AuthenticationException("Token expired");
            }

            // Create and set the context
            AccessTokenContext context = new AccessTokenContext(token, tokenId, subject, expiry);
            AccessTokenContext.set(context);

            // Get or create session and initialize if new
            SessionManager.Session session = sessionManager.getOrCreateSession(context.getSessionKey());
            session.setExpiry(expiry);

            // Initialize session data if not already loaded
            if (!session.isDataLoaded() && sessionInitializer != null) {
                sessionInitializer.accept(session);
            }

            LOG.debug("Access token context set for subject: {}, session: {}", subject, context.getSessionKey());

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to decode JWT token on {}: {}", requestURI, e.getMessage());
            throw new AuthenticationException(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        }
    }

    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
    public void processingCompletedNormally(HttpServletRequest request, HttpServletResponse response) {
        AccessTokenContext.clear();
    }

    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
    public void processingCompleted(HttpServletRequest request, HttpServletResponse response) {
        // Ensure context is always cleared, even on errors
        AccessTokenContext.clear();
    }

    /**
     * Check if the endpoint is public (doesn't require authentication).
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        // CapabilityStatement (metadata) is always public
        if (requestURI.endsWith("/metadata")) {
            return true;
        }

        // StructureDefinition and ImplementationGuide are public for conformance
        if (requestURI.contains("/StructureDefinition") || requestURI.contains("/ImplementationGuide")) {
            return true;
        }

        // Questionnaire is public (shared definitions, not user data)
        // Note: QuestionnaireResponse is NOT public (it's user data)
        if (requestURI.contains("/Questionnaire") && !requestURI.contains("/QuestionnaireResponse")) {
            return true;
        }

        // Swagger/OpenAPI endpoints
        if (requestURI.contains("/swagger-ui") || requestURI.contains("/api-docs")) {
            return true;
        }

        return false;
    }
}
