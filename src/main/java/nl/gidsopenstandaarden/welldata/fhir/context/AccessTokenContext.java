package nl.gidsopenstandaarden.welldata.fhir.context;

import java.time.Instant;

/**
 * Holds the access token context for the current request.
 * This context is used to scope resources to a specific user/session.
 */
public class AccessTokenContext {

    private static final ThreadLocal<AccessTokenContext> CONTEXT = new ThreadLocal<>();

    private final String token;
    private final String tokenId;  // jti claim or hash of token
    private final String subject;  // sub claim
    private final Instant expiry;  // exp claim

    public AccessTokenContext(String token, String tokenId, String subject, Instant expiry) {
        this.token = token;
        this.tokenId = tokenId;
        this.subject = subject;
        this.expiry = expiry;
    }

    public String getToken() {
        return token;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getExpiry() {
        return expiry;
    }

    /**
     * Get the session key used for scoping resources.
     * Uses tokenId if available, otherwise uses subject.
     */
    public String getSessionKey() {
        if (tokenId != null && !tokenId.isEmpty()) {
            return tokenId;
        }
        return subject;
    }

    /**
     * Check if the token has expired.
     */
    public boolean isExpired() {
        return expiry != null && Instant.now().isAfter(expiry);
    }

    /**
     * Set the current request's access token context.
     */
    public static void set(AccessTokenContext context) {
        CONTEXT.set(context);
    }

    /**
     * Get the current request's access token context.
     */
    public static AccessTokenContext get() {
        return CONTEXT.get();
    }

    /**
     * Clear the current request's access token context.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Check if there is a current access token context.
     */
    public static boolean isPresent() {
        return CONTEXT.get() != null;
    }
}
