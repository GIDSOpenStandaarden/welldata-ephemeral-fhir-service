package nl.gidsopenstandaarden.welldata.fhir.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AccessTokenContext.
 */
class AccessTokenContextTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        AccessTokenContext.clear();
    }

    @Test
    void testConstructorAndGetters() {
        String token = "test-token";
        String tokenId = "jti-123";
        String subject = "https://pod.example.com/user#me";
        Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);

        AccessTokenContext context = new AccessTokenContext(token, tokenId, subject, expiry);

        assertEquals(token, context.getToken());
        assertEquals(tokenId, context.getTokenId());
        assertEquals(subject, context.getSubject());
        assertEquals(expiry, context.getExpiry());
    }

    @Test
    void testGetSessionKeyWithTokenId() {
        AccessTokenContext context = new AccessTokenContext(
            "token", "jti-123", "subject", Instant.now()
        );

        assertEquals("jti-123", context.getSessionKey());
    }

    @Test
    void testGetSessionKeyWithoutTokenId() {
        AccessTokenContext context = new AccessTokenContext(
            "token", null, "https://pod.example.com/user#me", Instant.now()
        );

        assertEquals("https://pod.example.com/user#me", context.getSessionKey());
    }

    @Test
    void testGetSessionKeyWithEmptyTokenId() {
        AccessTokenContext context = new AccessTokenContext(
            "token", "", "subject", Instant.now()
        );

        assertEquals("subject", context.getSessionKey());
    }

    @Test
    void testIsExpiredWhenNotExpired() {
        Instant futureExpiry = Instant.now().plus(1, ChronoUnit.HOURS);
        AccessTokenContext context = new AccessTokenContext(
            "token", "jti", "subject", futureExpiry
        );

        assertFalse(context.isExpired());
    }

    @Test
    void testIsExpiredWhenExpired() {
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        AccessTokenContext context = new AccessTokenContext(
            "token", "jti", "subject", pastExpiry
        );

        assertTrue(context.isExpired());
    }

    @Test
    void testIsExpiredWhenNoExpiry() {
        AccessTokenContext context = new AccessTokenContext(
            "token", "jti", "subject", null
        );

        assertFalse(context.isExpired());
    }

    @Test
    void testThreadLocalSetAndGet() {
        assertNull(AccessTokenContext.get());
        assertFalse(AccessTokenContext.isPresent());

        AccessTokenContext context = new AccessTokenContext(
            "token", "jti", "subject", Instant.now()
        );
        AccessTokenContext.set(context);

        assertNotNull(AccessTokenContext.get());
        assertTrue(AccessTokenContext.isPresent());
        assertEquals(context, AccessTokenContext.get());
    }

    @Test
    void testThreadLocalClear() {
        AccessTokenContext context = new AccessTokenContext(
            "token", "jti", "subject", Instant.now()
        );
        AccessTokenContext.set(context);
        assertTrue(AccessTokenContext.isPresent());

        AccessTokenContext.clear();

        assertNull(AccessTokenContext.get());
        assertFalse(AccessTokenContext.isPresent());
    }

    @Test
    void testThreadLocalIsolation() throws InterruptedException {
        AccessTokenContext mainContext = new AccessTokenContext(
            "main-token", "main-jti", "main-subject", Instant.now()
        );
        AccessTokenContext.set(mainContext);

        // Verify context is set in main thread
        assertEquals("main-jti", AccessTokenContext.get().getTokenId());

        // Create a flag to track thread execution
        final boolean[] threadCompleted = {false};
        final AccessTokenContext[] threadContext = {null};

        Thread otherThread = new Thread(() -> {
            // Other thread should not see main thread's context
            threadContext[0] = AccessTokenContext.get();
            threadCompleted[0] = true;
        });

        otherThread.start();
        otherThread.join();

        assertTrue(threadCompleted[0]);
        assertNull(threadContext[0], "Other thread should not see main thread's context");

        // Main thread context should still be intact
        assertEquals("main-jti", AccessTokenContext.get().getTokenId());
    }
}
