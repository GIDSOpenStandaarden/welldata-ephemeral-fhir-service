package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import nl.gidsopenstandaarden.welldata.fhir.service.SolidPodClient;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WellDataResourceProvider.
 */
class WellDataResourceProviderTest {

    private FhirContext fhirContext;
    private SessionManager sessionManager;
    private SolidPodClient solidPodClient;
    private WellDataResourceProvider<Patient> provider;

    private static final String SESSION_KEY = "test-session";

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        sessionManager = new SessionManager();
        solidPodClient = mock(SolidPodClient.class);
        when(solidPodClient.isEnabled()).thenReturn(false); // Disable pod sync for unit tests

        provider = new WellDataResourceProvider<>(fhirContext, Patient.class, sessionManager, solidPodClient);

        // Set up access token context
        AccessTokenContext context = new AccessTokenContext(
            "test-token", SESSION_KEY, "https://pod.example.com/user#me",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
        AccessTokenContext.set(context);

        // Create the session
        sessionManager.getOrCreateSession(SESSION_KEY);
    }

    @AfterEach
    void tearDown() {
        AccessTokenContext.clear();
    }

    @Test
    void testGetResourceType() {
        assertEquals(Patient.class, provider.getResourceType());
    }

    @Test
    void testStoreAndRead() {
        Patient patient = new Patient();
        patient.setId("test-1");
        patient.setActive(true);

        provider.store(patient);

        Patient retrieved = provider.read(new IdType("Patient", "test-1"), null);

        assertNotNull(retrieved);
        assertTrue(retrieved.getActive());
        assertEquals("1", retrieved.getMeta().getVersionId());
    }

    @Test
    void testStoreAutoGeneratesVersion() {
        Patient patient = new Patient();
        patient.setId("test-1");

        provider.store(patient);

        Patient retrieved = provider.read(new IdType("Patient", "test-1"), null);
        assertEquals("1", retrieved.getMeta().getVersionId());
        assertNotNull(retrieved.getMeta().getLastUpdated());
    }

    @Test
    void testReadWithVersion() {
        SessionManager.Session session = sessionManager.getSession(SESSION_KEY);

        // Store version 1 directly to session
        Patient v1 = new Patient();
        v1.setId("Patient/versioned/_history/1");
        v1.setActive(false);
        session.storeResource("Patient", "versioned", 1L, v1);

        // Store version 2 directly to session
        Patient v2 = new Patient();
        v2.setId("Patient/versioned/_history/2");
        v2.setActive(true);
        session.storeResource("Patient", "versioned", 2L, v2);

        // Read specific version
        Patient readV1 = provider.read(new IdType("Patient", "versioned", "1"), null);
        assertFalse(readV1.getActive());

        Patient readV2 = provider.read(new IdType("Patient", "versioned", "2"), null);
        assertTrue(readV2.getActive());

        // Read latest (no version specified)
        Patient readLatest = provider.read(new IdType("Patient", "versioned"), null);
        assertTrue(readLatest.getActive());
    }

    @Test
    void testReadNotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
            provider.read(new IdType("Patient", "nonexistent"), null)
        );
    }

    @Test
    void testReadDeletedResource() {
        Patient patient = new Patient();
        patient.setId("deleted-patient");
        provider.store(patient);

        // Delete the resource
        SessionManager.Session session = sessionManager.getSession(SESSION_KEY);
        session.deleteResource("Patient", "deleted-patient");

        assertThrows(ResourceGoneException.class, () ->
            provider.read(new IdType("Patient", "deleted-patient"), null)
        );
    }

    @Test
    void testReadWithoutSession() {
        AccessTokenContext.clear();

        assertThrows(AuthenticationException.class, () ->
            provider.read(new IdType("Patient", "test"), null)
        );
    }

    @Test
    void testCreate() {
        Patient patient = new Patient();
        patient.setActive(true);

        var outcome = provider.create(patient, null);

        assertTrue(outcome.getCreated());
        assertNotNull(outcome.getId());
        assertNotNull(outcome.getResource());

        String id = outcome.getId().getIdPart();
        Patient retrieved = provider.read(new IdType("Patient", id), null);
        assertTrue(retrieved.getActive());
    }

    @Test
    void testCreateWithPodEnabled() {
        when(solidPodClient.isEnabled()).thenReturn(true);

        Patient patient = new Patient();
        patient.setActive(true);

        provider.create(patient, null);

        verify(solidPodClient).isEnabled();
        // saveResource should be called when pod is enabled
        verify(solidPodClient).saveResource(any(), eq("test-token"));
    }

    @Test
    void testUpdate() {
        // First create
        Patient patient = new Patient();
        patient.setActive(false);
        var createOutcome = provider.create(patient, null);
        String id = createOutcome.getId().getIdPart();

        // Then update
        Patient updated = new Patient();
        updated.setActive(true);

        var updateOutcome = provider.update(new IdType("Patient", id), updated, null);

        assertNotNull(updateOutcome.getId());
        assertEquals("2", updateOutcome.getId().getVersionIdPart());

        Patient retrieved = provider.read(new IdType("Patient", id), null);
        assertTrue(retrieved.getActive());
    }

    @Test
    void testUpdateNonExistentCreatesNewResource() {
        Patient patient = new Patient();
        patient.setActive(true);

        var outcome = provider.update(new IdType("Patient", "new-id"), patient, null);

        assertEquals("1", outcome.getId().getVersionIdPart());

        Patient retrieved = provider.read(new IdType("Patient", "new-id"), null);
        assertTrue(retrieved.getActive());
    }

    @Test
    void testDelete() {
        Patient patient = new Patient();
        patient.setId("to-delete");
        provider.store(patient);

        var outcome = provider.delete(new IdType("Patient", "to-delete"), null);

        assertNotNull(outcome);
        assertThrows(ResourceGoneException.class, () ->
            provider.read(new IdType("Patient", "to-delete"), null)
        );
    }

    @Test
    void testDeleteNonExistent() {
        assertThrows(ResourceNotFoundException.class, () ->
            provider.delete(new IdType("Patient", "nonexistent"), null)
        );
    }

    @Test
    void testDeleteWithPodEnabled() {
        when(solidPodClient.isEnabled()).thenReturn(true);

        Patient patient = new Patient();
        patient.setId("to-delete");
        provider.store(patient);

        provider.delete(new IdType("Patient", "to-delete"), null);

        verify(solidPodClient).deleteResource("Patient", "to-delete", "test-token");
    }

    @Test
    void testSearch() {
        Patient p1 = new Patient();
        p1.setId("p1");
        provider.store(p1);

        Patient p2 = new Patient();
        p2.setId("p2");
        provider.store(p2);

        var results = provider.search(null);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void testSearchExcludesDeletedResources() {
        Patient p1 = new Patient();
        p1.setId("p1");
        provider.store(p1);

        Patient p2 = new Patient();
        p2.setId("p2");
        provider.store(p2);

        // Delete p1
        SessionManager.Session session = sessionManager.getSession(SESSION_KEY);
        session.deleteResource("Patient", "p1");

        var results = provider.search(null);

        assertEquals(1, results.size());
    }

    @Test
    void testGetAllResources() {
        Patient p1 = new Patient();
        p1.setId("p1");
        provider.store(p1);

        Patient p2 = new Patient();
        p2.setId("p2");
        provider.store(p2);

        List<Patient> all = provider.getAllResources();

        assertEquals(2, all.size());
    }

    @Test
    void testGetResourceCount() {
        assertEquals(0, provider.getResourceCount());

        Patient p1 = new Patient();
        p1.setId("p1");
        provider.store(p1);

        assertEquals(1, provider.getResourceCount());

        Patient p2 = new Patient();
        p2.setId("p2");
        provider.store(p2);

        assertEquals(2, provider.getResourceCount());
    }

    @Test
    void testClear() {
        Patient p1 = new Patient();
        p1.setId("p1");
        provider.store(p1);

        assertEquals(1, provider.getResourceCount());

        provider.clear();

        assertEquals(0, provider.getResourceCount());
    }

    @Test
    void testResourceCloning() {
        // Ensure modifications to returned resources don't affect stored resources
        Patient original = new Patient();
        original.setId("cloning-test");
        original.setActive(false);
        provider.store(original);

        Patient retrieved1 = provider.read(new IdType("Patient", "cloning-test"), null);
        retrieved1.setActive(true); // Modify the retrieved resource

        Patient retrieved2 = provider.read(new IdType("Patient", "cloning-test"), null);
        assertFalse(retrieved2.getActive()); // Should still be false
    }

    @Test
    void testSessionIsolation() {
        // Store in first session
        Patient patient = new Patient();
        patient.setId("isolated");
        provider.store(patient);

        // Switch to different session
        String otherSessionKey = "other-session";
        AccessTokenContext otherContext = new AccessTokenContext(
            "other-token", otherSessionKey, "other-subject",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
        AccessTokenContext.set(otherContext);
        sessionManager.getOrCreateSession(otherSessionKey);

        // Should not find the resource from first session
        assertThrows(ResourceNotFoundException.class, () ->
            provider.read(new IdType("Patient", "isolated"), null)
        );
    }
}
