package nl.gidsopenstandaarden.welldata.fhir.service;

import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionManager.
 */
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        AccessTokenContext.clear();
    }

    @AfterEach
    void tearDown() {
        AccessTokenContext.clear();
    }

    @Test
    void testGetOrCreateSession() {
        String sessionKey = "test-session-1";

        SessionManager.Session session1 = sessionManager.getOrCreateSession(sessionKey);
        assertNotNull(session1);
        assertEquals(sessionKey, session1.getSessionKey());

        // Should return same session
        SessionManager.Session session2 = sessionManager.getOrCreateSession(sessionKey);
        assertSame(session1, session2);
    }

    @Test
    void testGetCurrentSessionWithNoContext() {
        assertNull(sessionManager.getCurrentSession());
    }

    @Test
    void testGetCurrentSessionWithContext() {
        String sessionKey = "context-session";
        AccessTokenContext context = new AccessTokenContext(
            "token", sessionKey, "subject", Instant.now().plus(1, ChronoUnit.HOURS)
        );
        AccessTokenContext.set(context);

        // Session doesn't exist yet
        assertNull(sessionManager.getCurrentSession());

        // Create the session
        sessionManager.getOrCreateSession(sessionKey);

        // Now it should return the session
        SessionManager.Session session = sessionManager.getCurrentSession();
        assertNotNull(session);
        assertEquals(sessionKey, session.getSessionKey());
    }

    @Test
    void testGetSession() {
        String sessionKey = "get-session-test";
        assertNull(sessionManager.getSession(sessionKey));

        sessionManager.getOrCreateSession(sessionKey);

        SessionManager.Session session = sessionManager.getSession(sessionKey);
        assertNotNull(session);
        assertEquals(sessionKey, session.getSessionKey());
    }

    @Test
    void testRemoveSession() {
        String sessionKey = "remove-session-test";
        sessionManager.getOrCreateSession(sessionKey);
        assertNotNull(sessionManager.getSession(sessionKey));

        sessionManager.removeSession(sessionKey);

        assertNull(sessionManager.getSession(sessionKey));
    }

    @Test
    void testGetActiveSessionKeys() {
        sessionManager.getOrCreateSession("session-a");
        sessionManager.getOrCreateSession("session-b");
        sessionManager.getOrCreateSession("session-c");

        Set<String> keys = sessionManager.getActiveSessionKeys();

        assertEquals(3, keys.size());
        assertTrue(keys.contains("session-a"));
        assertTrue(keys.contains("session-b"));
        assertTrue(keys.contains("session-c"));
    }

    @Test
    void testCleanupExpiredSessions() {
        // Create a non-expired session
        SessionManager.Session activeSession = sessionManager.getOrCreateSession("active");
        activeSession.setExpiry(Instant.now().plus(1, ChronoUnit.HOURS));

        // Create an expired session
        SessionManager.Session expiredSession = sessionManager.getOrCreateSession("expired");
        expiredSession.setExpiry(Instant.now().minus(1, ChronoUnit.HOURS));

        assertEquals(2, sessionManager.getActiveSessionKeys().size());

        sessionManager.cleanupExpiredSessions();

        Set<String> remaining = sessionManager.getActiveSessionKeys();
        assertEquals(1, remaining.size());
        assertTrue(remaining.contains("active"));
        assertFalse(remaining.contains("expired"));
    }

    // Tests for Session inner class

    @Test
    void testSessionCreation() {
        SessionManager.Session session = sessionManager.getOrCreateSession("test");

        assertNotNull(session.getCreatedAt());
        assertNull(session.getExpiry());
        assertFalse(session.isDataLoaded());
    }

    @Test
    void testSessionExpiry() {
        SessionManager.Session session = sessionManager.getOrCreateSession("expiry-test");

        // No expiry set - should not be expired
        assertFalse(session.isExpired(Instant.now()));

        // Set future expiry
        session.setExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        assertFalse(session.isExpired(Instant.now()));

        // Set past expiry
        session.setExpiry(Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(session.isExpired(Instant.now()));
    }

    @Test
    void testSessionDataLoadedFlag() {
        SessionManager.Session session = sessionManager.getOrCreateSession("data-loaded-test");

        assertFalse(session.isDataLoaded());

        session.setDataLoaded(true);
        assertTrue(session.isDataLoaded());

        session.setDataLoaded(false);
        assertFalse(session.isDataLoaded());
    }

    @Test
    void testSessionStoreAndGetResource() {
        SessionManager.Session session = sessionManager.getOrCreateSession("resource-test");

        Patient patient = new Patient();
        patient.setId("Patient/123");

        session.storeResource("Patient", "123", 1L, patient);

        Patient retrieved = session.getResource("Patient", "123", null);
        assertNotNull(retrieved);
        assertEquals("Patient/123", retrieved.getId());
    }

    @Test
    void testSessionGetResourceByVersion() {
        SessionManager.Session session = sessionManager.getOrCreateSession("version-test");

        Patient v1 = new Patient();
        v1.setId("Patient/123/_history/1");
        session.storeResource("Patient", "123", 1L, v1);

        Patient v2 = new Patient();
        v2.setId("Patient/123/_history/2");
        v2.setActive(true);
        session.storeResource("Patient", "123", 2L, v2);

        // Get specific version
        Patient retrievedV1 = session.getResource("Patient", "123", 1L);
        assertEquals("Patient/123/_history/1", retrievedV1.getId());

        // Get latest version (null version)
        Patient retrievedLatest = session.getResource("Patient", "123", null);
        assertEquals("Patient/123/_history/2", retrievedLatest.getId());
        assertTrue(retrievedLatest.getActive());
    }

    @Test
    void testSessionGetAllResources() {
        SessionManager.Session session = sessionManager.getOrCreateSession("all-resources-test");

        Patient p1 = new Patient();
        p1.setId("Patient/1");
        session.storeResource("Patient", "1", 1L, p1);

        Patient p2 = new Patient();
        p2.setId("Patient/2");
        session.storeResource("Patient", "2", 1L, p2);

        Observation obs = new Observation();
        obs.setId("Observation/1");
        session.storeResource("Observation", "1", 1L, obs);

        List<Patient> patients = session.getAllResources("Patient");
        assertEquals(2, patients.size());

        List<Observation> observations = session.getAllResources("Observation");
        assertEquals(1, observations.size());
    }

    @Test
    void testSessionDeleteResource() {
        SessionManager.Session session = sessionManager.getOrCreateSession("delete-test");

        Patient patient = new Patient();
        session.storeResource("Patient", "123", 1L, patient);

        assertTrue(session.resourceExists("Patient", "123"));
        assertFalse(session.isDeleted("Patient", "123"));

        session.deleteResource("Patient", "123");

        assertTrue(session.isDeleted("Patient", "123"));
        assertFalse(session.resourceExists("Patient", "123"));

        // Deleted resources should not appear in getAllResources
        List<Patient> all = session.getAllResources("Patient");
        assertTrue(all.isEmpty());
    }

    @Test
    void testSessionGetAndIncrementNextId() {
        SessionManager.Session session = sessionManager.getOrCreateSession("next-id-test");

        assertEquals(1, session.getAndIncrementNextId("Patient"));
        assertEquals(2, session.getAndIncrementNextId("Patient"));
        assertEquals(3, session.getAndIncrementNextId("Patient"));

        // Different resource type starts at 1
        assertEquals(1, session.getAndIncrementNextId("Observation"));
    }

    @Test
    void testSessionClear() {
        SessionManager.Session session = sessionManager.getOrCreateSession("clear-test");

        Patient patient = new Patient();
        session.storeResource("Patient", "1", 1L, patient);
        session.getAndIncrementNextId("Patient");
        session.setDataLoaded(true);

        assertEquals(1, session.getAllResources("Patient").size());

        session.clear();

        assertEquals(0, session.getAllResources("Patient").size());
        assertFalse(session.isDataLoaded());
        assertEquals(1, session.getAndIncrementNextId("Patient")); // Reset to 1
    }

    @Test
    void testSessionResourceIsolation() {
        SessionManager.Session session1 = sessionManager.getOrCreateSession("session-1");
        SessionManager.Session session2 = sessionManager.getOrCreateSession("session-2");

        Patient p1 = new Patient();
        p1.setId("Patient/1");
        session1.storeResource("Patient", "1", 1L, p1);

        // Session 2 should not see session 1's resource
        assertTrue(session1.resourceExists("Patient", "1"));
        assertFalse(session2.resourceExists("Patient", "1"));
        assertEquals(1, session1.getAllResources("Patient").size());
        assertEquals(0, session2.getAllResources("Patient").size());
    }
}
