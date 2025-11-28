package nl.gidsopenstandaarden.welldata.fhir.e2e;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import nl.gidsopenstandaarden.welldata.fhir.Application;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the FHIR server using Spring Boot test server.
 *
 * These tests start the full Spring Boot application and test the HTTP API
 * without needing Docker/Testcontainers.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "welldata.solid.enabled=false",
        "welldata.ig.url=",
        "welldata.testdata.enabled=false"
    }
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FhirServerIntegrationTest {

    private static final FhirContext fhirContext = FhirContext.forR4();

    @LocalServerPort
    private int port;

    private String baseUrl;
    private IGenericClient fhirClient;
    private String testToken;

    // Store IDs across tests
    private static String createdPatientId;
    private static String createdObservationId;
    private static String createdQuestionnaireResponseId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/fhir";

        // Create a test JWT token
        testToken = createTestToken("integration-test-user", "https://pod.example.com/user#me");

        // Create HAPI FHIR client with auth
        fhirClient = fhirContext.newRestfulGenericClient(baseUrl);
        fhirClient.registerInterceptor(new BearerTokenAuthInterceptor(testToken));
    }

    /**
     * Creates a minimal JWT token for testing.
     */
    private String createTestToken(String jti, String subject) {
        Algorithm algorithm = Algorithm.HMAC256("test-secret-for-integration-tests");
        return JWT.create()
            .withJWTId(jti)
            .withSubject(subject)
            .withIssuedAt(new Date())
            .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
            .sign(algorithm);
    }

    // ==================== Metadata Tests ====================

    @Test
    @Order(1)
    void testMetadataEndpoint() {
        // Metadata should be accessible without authentication
        IGenericClient unauthenticatedClient = fhirContext.newRestfulGenericClient(baseUrl);

        CapabilityStatement metadata = unauthenticatedClient
            .capabilities()
            .ofType(CapabilityStatement.class)
            .execute();

        assertNotNull(metadata);
        assertEquals("CapabilityStatement", metadata.getResourceType().name());
        assertEquals("4.0.1", metadata.getFhirVersion().toCode());
        assertEquals(Enumerations.PublicationStatus.ACTIVE, metadata.getStatus());
    }

    @Test
    @Order(2)
    void testMetadataContainsExpectedResources() {
        IGenericClient unauthenticatedClient = fhirContext.newRestfulGenericClient(baseUrl);

        CapabilityStatement metadata = unauthenticatedClient
            .capabilities()
            .ofType(CapabilityStatement.class)
            .execute();

        // Check that expected resource types are present
        var restResources = metadata.getRestFirstRep().getResource();
        var resourceTypes = restResources.stream()
            .map(r -> r.getType())
            .toList();

        assertTrue(resourceTypes.contains("Patient"));
        assertTrue(resourceTypes.contains("Observation"));
        assertTrue(resourceTypes.contains("Questionnaire"));
        assertTrue(resourceTypes.contains("QuestionnaireResponse"));
    }

    // ==================== Patient CRUD Tests ====================

    @Test
    @Order(10)
    void testCreatePatient() {
        Patient patient = new Patient();
        patient.addName().setFamily("IntegrationTest").addGiven("Patient");
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.setBirthDateElement(new DateType("1985-06-15"));
        patient.setActive(true);

        MethodOutcome outcome = fhirClient.create()
            .resource(patient)
            .execute();

        assertTrue(outcome.getCreated());
        assertNotNull(outcome.getId());

        createdPatientId = outcome.getId().getIdPart();
        assertNotNull(createdPatientId);
    }

    @Test
    @Order(11)
    void testReadPatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        Patient patient = fhirClient.read()
            .resource(Patient.class)
            .withId(createdPatientId)
            .execute();

        assertNotNull(patient);
        assertEquals(createdPatientId, patient.getIdElement().getIdPart());
        assertEquals("IntegrationTest", patient.getNameFirstRep().getFamily());
        assertEquals("1", patient.getMeta().getVersionId());
    }

    @Test
    @Order(12)
    void testUpdatePatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        Patient patient = fhirClient.read()
            .resource(Patient.class)
            .withId(createdPatientId)
            .execute();

        // Update the patient
        patient.getNameFirstRep().setFamily("UpdatedFamily");
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);

        MethodOutcome outcome = fhirClient.update()
            .resource(patient)
            .execute();

        assertNotNull(outcome.getId());
        assertEquals("2", outcome.getId().getVersionIdPart());

        // Verify the update
        Patient updated = fhirClient.read()
            .resource(Patient.class)
            .withId(createdPatientId)
            .execute();

        assertEquals("UpdatedFamily", updated.getNameFirstRep().getFamily());
        assertEquals(Enumerations.AdministrativeGender.FEMALE, updated.getGender());
    }

    @Test
    @Order(13)
    void testSearchPatients() {
        Bundle bundle = fhirClient.search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .execute();

        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    // ==================== Observation CRUD Tests ====================

    @Test
    @Order(20)
    void testCreateObservation() {
        assertNotNull(createdPatientId, "Patient must be created first");

        Observation observation = new Observation();
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setSubject(new Reference("Patient/" + createdPatientId));
        observation.setCode(new CodeableConcept().addCoding(
            new Coding("http://snomed.info/sct", "27113001", "Body weight")
        ));
        observation.addCategory(new CodeableConcept().addCoding(
            new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")
        ));
        observation.setValue(new Quantity()
            .setValue(72.5)
            .setUnit("kg")
            .setSystem("http://unitsofmeasure.org")
            .setCode("kg"));
        observation.setEffective(new DateTimeType(new Date()));

        MethodOutcome outcome = fhirClient.create()
            .resource(observation)
            .execute();

        assertTrue(outcome.getCreated());
        createdObservationId = outcome.getId().getIdPart();
        assertNotNull(createdObservationId);
    }

    @Test
    @Order(21)
    void testReadObservation() {
        assertNotNull(createdObservationId, "Observation must be created first");

        Observation observation = fhirClient.read()
            .resource(Observation.class)
            .withId(createdObservationId)
            .execute();

        assertNotNull(observation);
        assertEquals(Observation.ObservationStatus.FINAL, observation.getStatus());
        assertEquals("27113001", observation.getCode().getCodingFirstRep().getCode());
    }

    @Test
    @Order(22)
    void testSearchObservationBySubject() {
        assertNotNull(createdPatientId, "Patient must be created first");

        Bundle bundle = fhirClient.search()
            .forResource(Observation.class)
            .where(Observation.SUBJECT.hasId("Patient/" + createdPatientId))
            .returnBundle(Bundle.class)
            .execute();

        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test
    @Order(23)
    void testSearchObservationByCode() {
        Bundle bundle = fhirClient.search()
            .forResource(Observation.class)
            .where(Observation.CODE.exactly().code("27113001"))
            .returnBundle(Bundle.class)
            .execute();

        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    @Test
    @Order(24)
    void testSearchObservationByCategory() {
        Bundle bundle = fhirClient.search()
            .forResource(Observation.class)
            .where(Observation.CATEGORY.exactly().code("vital-signs"))
            .returnBundle(Bundle.class)
            .execute();

        assertNotNull(bundle);
        assertTrue(bundle.getEntry().size() >= 1);
    }

    // ==================== QuestionnaireResponse Tests ====================

    @Test
    @Order(30)
    void testCreateQuestionnaireResponse() {
        assertNotNull(createdPatientId, "Patient must be created first");

        QuestionnaireResponse response = new QuestionnaireResponse();
        response.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
        response.setSubject(new Reference("Patient/" + createdPatientId));
        response.setAuthored(new Date());
        response.setQuestionnaire("Questionnaire/test-questionnaire");

        // Add a simple answer
        QuestionnaireResponse.QuestionnaireResponseItemComponent item = response.addItem();
        item.setLinkId("q1");
        item.setText("How are you feeling?");
        item.addAnswer().setValue(new StringType("Great"));

        MethodOutcome outcome = fhirClient.create()
            .resource(response)
            .execute();

        assertTrue(outcome.getCreated());
        createdQuestionnaireResponseId = outcome.getId().getIdPart();
        assertNotNull(createdQuestionnaireResponseId);
    }

    @Test
    @Order(31)
    void testReadQuestionnaireResponse() {
        assertNotNull(createdQuestionnaireResponseId, "QuestionnaireResponse must be created first");

        QuestionnaireResponse response = fhirClient.read()
            .resource(QuestionnaireResponse.class)
            .withId(createdQuestionnaireResponseId)
            .execute();

        assertNotNull(response);
        assertEquals(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED, response.getStatus());
    }

    // ==================== Session Isolation Tests ====================

    @Test
    @Order(40)
    void testSessionIsolation() {
        // First, verify the main user has at least one patient
        Bundle mainUserBundle = fhirClient.search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .execute();
        int mainUserPatientCount = mainUserBundle.getEntry().size();
        assertTrue(mainUserPatientCount >= 1,
            "Main user should have at least one patient, but has " + mainUserPatientCount);

        // Create two separate users and verify they can't see each other's data
        // User A creates a patient
        String userAJti = "user-a-" + java.util.UUID.randomUUID();
        String userAToken = createTestToken(userAJti, "https://pod.example.com/userA#me");
        IGenericClient userAClient = fhirContext.newRestfulGenericClient(baseUrl);
        userAClient.registerInterceptor(new BearerTokenAuthInterceptor(userAToken));

        Patient patientA = new Patient();
        patientA.addName().setFamily("UserAPatient");
        userAClient.create().resource(patientA).execute();

        // Verify User A can see their patient
        Bundle userABundle = userAClient.search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .execute();
        assertEquals(1, userABundle.getEntry().size(), "User A should see 1 patient");

        // User B (completely different session) should see 0 patients
        String userBJti = "user-b-" + java.util.UUID.randomUUID();
        String userBToken = createTestToken(userBJti, "https://pod.example.com/userB#me");
        IGenericClient userBClient = fhirContext.newRestfulGenericClient(baseUrl);
        userBClient.registerInterceptor(new BearerTokenAuthInterceptor(userBToken));

        Bundle userBBundle = userBClient.search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .execute();

        assertEquals(0, userBBundle.getEntry().size(),
            "User B should have 0 patients (session isolation). " +
            "But found: " + userBBundle.getEntry().stream()
                .map(e -> ((Patient)e.getResource()).getNameFirstRep().getFamily())
                .toList());
    }

    // ==================== Delete Tests ====================

    @Test
    @Order(50)
    void testDeleteObservation() {
        assertNotNull(createdObservationId, "Observation must be created first");

        fhirClient.delete()
            .resourceById("Observation", createdObservationId)
            .execute();

        // Verify it's gone (should throw exception)
        try {
            fhirClient.read()
                .resource(Observation.class)
                .withId(createdObservationId)
                .execute();
            fail("Expected ResourceGoneException");
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceGoneException e) {
            // Expected
        }
    }

    @Test
    @Order(51)
    void testDeleteQuestionnaireResponse() {
        assertNotNull(createdQuestionnaireResponseId, "QuestionnaireResponse must be created first");

        fhirClient.delete()
            .resourceById("QuestionnaireResponse", createdQuestionnaireResponseId)
            .execute();

        // Verify it's gone
        try {
            fhirClient.read()
                .resource(QuestionnaireResponse.class)
                .withId(createdQuestionnaireResponseId)
                .execute();
            fail("Expected ResourceGoneException");
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceGoneException e) {
            // Expected
        }
    }

    @Test
    @Order(52)
    void testDeletePatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        fhirClient.delete()
            .resourceById("Patient", createdPatientId)
            .execute();

        // Verify it's gone
        try {
            fhirClient.read()
                .resource(Patient.class)
                .withId(createdPatientId)
                .execute();
            fail("Expected ResourceGoneException");
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceGoneException e) {
            // Expected
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(60)
    void testReadNonExistentResource() {
        try {
            fhirClient.read()
                .resource(Patient.class)
                .withId("nonexistent-id-12345")
                .execute();
            fail("Expected ResourceNotFoundException");
        } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
            // Expected
        }
    }

    // ==================== Multiple Observations Test ====================

    @Test
    @Order(70)
    void testCreateMultipleObservations() {
        // Create a patient for this test
        Patient patient = new Patient();
        patient.addName().setFamily("MultiObs").addGiven("Test");
        MethodOutcome patientOutcome = fhirClient.create().resource(patient).execute();
        String patientId = patientOutcome.getId().getIdPart();

        // Create multiple observations
        String[] codes = {"27113001", "60621009", "50373000"}; // Weight, BMI, Height
        String[] displays = {"Body weight", "BMI", "Body height"};

        for (int i = 0; i < codes.length; i++) {
            Observation obs = new Observation();
            obs.setStatus(Observation.ObservationStatus.FINAL);
            obs.setSubject(new Reference("Patient/" + patientId));
            obs.setCode(new CodeableConcept().addCoding(
                new Coding("http://snomed.info/sct", codes[i], displays[i])
            ));
            obs.addCategory(new CodeableConcept().addCoding(
                new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")
            ));
            obs.setValue(new Quantity().setValue(70 + i * 10).setUnit("units"));

            fhirClient.create().resource(obs).execute();
        }

        // Search and verify all observations are returned
        Bundle bundle = fhirClient.search()
            .forResource(Observation.class)
            .where(Observation.SUBJECT.hasId("Patient/" + patientId))
            .returnBundle(Bundle.class)
            .execute();

        assertEquals(3, bundle.getEntry().size());
    }
}
