package nl.gidsopenstandaarden.welldata.fhir.e2e;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the FHIR server using Testcontainers.
 *
 * These tests spin up a real Docker container with the FHIR server
 * and test the full HTTP API.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_DOCKER_TESTS",
    matches = "true",
    disabledReason = "Docker-based E2E tests require Docker and RUN_DOCKER_TESTS=true"
)
class FhirServerE2ETest {

    private static final FhirContext fhirContext = FhirContext.forR4();

    @Container
    static GenericContainer<?> fhirServer = new GenericContainer<>(
        new ImageFromDockerfile()
            .withDockerfile(Paths.get("Dockerfile"))
    )
        .withExposedPorts(8080)
        .withEnv("SPRING_PROFILES_ACTIVE", "test")
        .withEnv("WELLDATA_SOLID_ENABLED", "false")
        .withEnv("WELLDATA_IG_PACKAGE_URL", "")
        .waitingFor(Wait.forHttp("/fhir/metadata")
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofMinutes(2)));

    private static String baseUrl;
    private static IGenericClient fhirClient;
    private static String testToken;
    private static String createdPatientId;
    private static String createdObservationId;

    @BeforeAll
    static void setUp() {
        baseUrl = "http://" + fhirServer.getHost() + ":" + fhirServer.getMappedPort(8080) + "/fhir";
        RestAssured.baseURI = baseUrl;

        // Create a test JWT token
        testToken = createTestToken("test-user-1", "https://pod.example.com/user1#me");

        // Create HAPI FHIR client with auth
        fhirClient = fhirContext.newRestfulGenericClient(baseUrl);
        fhirClient.registerInterceptor(new BearerTokenAuthInterceptor(testToken));
    }

    /**
     * Creates a minimal JWT token for testing.
     * In production, this would be a proper Solid OIDC token.
     */
    private static String createTestToken(String jti, String subject) {
        Algorithm algorithm = Algorithm.HMAC256("test-secret-for-e2e-tests-only");
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
    void testMetadataEndpointIsPublic() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get("/metadata")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("resourceType", equalTo("CapabilityStatement"))
            .body("fhirVersion", equalTo("4.0.1"))
            .body("status", equalTo("active"));
    }

    @Test
    @Order(2)
    void testMetadataContainsExpectedResources() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get("/metadata")
        .then()
            .statusCode(200)
            .body("rest[0].resource.type", hasItems("Patient", "Observation", "Questionnaire", "QuestionnaireResponse"));
    }

    // ==================== Authentication Tests ====================

    @Test
    @Order(10)
    void testUnauthorizedAccessIsRejected() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get("/Patient")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(11)
    void testInvalidTokenIsRejected() {
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer invalid-token")
        .when()
            .get("/Patient")
        .then()
            .statusCode(401);
    }

    // ==================== Patient CRUD Tests ====================

    @Test
    @Order(20)
    void testCreatePatient() {
        Patient patient = new Patient();
        patient.addName().setFamily("TestFamily").addGiven("TestGiven");
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.setBirthDateElement(new DateType("1990-01-15"));
        patient.setActive(true);

        String patientJson = fhirContext.newJsonParser().encodeResourceToString(patient);

        String locationHeader = given()
            .accept(ContentType.JSON)
            .contentType("application/fhir+json")
            .header("Authorization", "Bearer " + testToken)
            .body(patientJson)
        .when()
            .post("/Patient")
        .then()
            .statusCode(201)
            .header("Location", containsString("/Patient/"))
            .body("resourceType", equalTo("Patient"))
            .body("name[0].family", equalTo("TestFamily"))
            .extract()
            .header("Location");

        // Extract the created ID
        createdPatientId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
        if (createdPatientId.contains("/_history")) {
            createdPatientId = createdPatientId.substring(0, createdPatientId.indexOf("/_history"));
        }

        assertNotNull(createdPatientId);
    }

    @Test
    @Order(21)
    void testReadPatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Patient/" + createdPatientId)
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Patient"))
            .body("id", equalTo(createdPatientId))
            .body("name[0].family", equalTo("TestFamily"));
    }

    @Test
    @Order(22)
    void testUpdatePatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        Patient patient = new Patient();
        patient.setId(createdPatientId);
        patient.addName().setFamily("UpdatedFamily").addGiven("UpdatedGiven");
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        patient.setActive(true);

        String patientJson = fhirContext.newJsonParser().encodeResourceToString(patient);

        given()
            .accept(ContentType.JSON)
            .contentType("application/fhir+json")
            .header("Authorization", "Bearer " + testToken)
            .body(patientJson)
        .when()
            .put("/Patient/" + createdPatientId)
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Patient"))
            .body("name[0].family", equalTo("UpdatedFamily"))
            .body("meta.versionId", equalTo("2"));
    }

    @Test
    @Order(23)
    void testSearchPatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Patient")
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Bundle"))
            .body("entry.size()", greaterThanOrEqualTo(1));
    }

    // ==================== Observation CRUD Tests ====================

    @Test
    @Order(30)
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
            .setValue(75.5)
            .setUnit("kg")
            .setSystem("http://unitsofmeasure.org")
            .setCode("kg"));
        observation.setEffective(new DateTimeType(new Date()));

        String observationJson = fhirContext.newJsonParser().encodeResourceToString(observation);

        String locationHeader = given()
            .accept(ContentType.JSON)
            .contentType("application/fhir+json")
            .header("Authorization", "Bearer " + testToken)
            .body(observationJson)
        .when()
            .post("/Observation")
        .then()
            .statusCode(201)
            .body("resourceType", equalTo("Observation"))
            .body("status", equalTo("final"))
            .body("valueQuantity.value", equalTo(75.5f))
            .extract()
            .header("Location");

        createdObservationId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
        if (createdObservationId.contains("/_history")) {
            createdObservationId = createdObservationId.substring(0, createdObservationId.indexOf("/_history"));
        }

        assertNotNull(createdObservationId);
    }

    @Test
    @Order(31)
    void testReadObservation() {
        assertNotNull(createdObservationId, "Observation must be created first");

        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Observation/" + createdObservationId)
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Observation"))
            .body("id", equalTo(createdObservationId));
    }

    @Test
    @Order(32)
    void testSearchObservationBySubject() {
        assertNotNull(createdPatientId, "Patient must be created first");

        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
            .queryParam("subject", "Patient/" + createdPatientId)
        .when()
            .get("/Observation")
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Bundle"))
            .body("entry.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(33)
    void testSearchObservationByCode() {
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
            .queryParam("code", "27113001")
        .when()
            .get("/Observation")
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Bundle"))
            .body("entry.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(34)
    void testSearchObservationByCategory() {
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
            .queryParam("category", "vital-signs")
        .when()
            .get("/Observation")
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Bundle"))
            .body("entry.size()", greaterThanOrEqualTo(1));
    }

    // ==================== Questionnaire Tests ====================

    @Test
    @Order(40)
    void testSearchQuestionnaires() {
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Questionnaire")
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Bundle"));
        // Note: May be empty if no static questionnaires are loaded in test mode
    }

    // ==================== QuestionnaireResponse Tests ====================

    @Test
    @Order(50)
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
        item.addAnswer().setValue(new StringType("Good"));

        String responseJson = fhirContext.newJsonParser().encodeResourceToString(response);

        given()
            .accept(ContentType.JSON)
            .contentType("application/fhir+json")
            .header("Authorization", "Bearer " + testToken)
            .body(responseJson)
        .when()
            .post("/QuestionnaireResponse")
        .then()
            .statusCode(201)
            .body("resourceType", equalTo("QuestionnaireResponse"))
            .body("status", equalTo("completed"));
    }

    // ==================== Session Isolation Tests ====================

    @Test
    @Order(60)
    void testSessionIsolation() {
        // Create a different user's token
        String otherUserToken = createTestToken("test-user-2", "https://pod.example.com/user2#me");

        // Other user should not see the first user's patient
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + otherUserToken)
        .when()
            .get("/Patient")
        .then()
            .statusCode(200)
            .body("resourceType", equalTo("Bundle"))
            .body("entry", anyOf(nullValue(), hasSize(0)));
    }

    // ==================== Delete Tests ====================

    @Test
    @Order(70)
    void testDeleteObservation() {
        assertNotNull(createdObservationId, "Observation must be created first");

        given()
            .header("Authorization", "Bearer " + testToken)
        .when()
            .delete("/Observation/" + createdObservationId)
        .then()
            .statusCode(200);

        // Verify it's gone (should return 410 Gone)
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Observation/" + createdObservationId)
        .then()
            .statusCode(410);
    }

    @Test
    @Order(71)
    void testDeletePatient() {
        assertNotNull(createdPatientId, "Patient must be created first");

        given()
            .header("Authorization", "Bearer " + testToken)
        .when()
            .delete("/Patient/" + createdPatientId)
        .then()
            .statusCode(200);

        // Verify it's gone
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Patient/" + createdPatientId)
        .then()
            .statusCode(410);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(80)
    void testReadNonExistentResource() {
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/Patient/nonexistent-id-12345")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(81)
    void testInvalidResourceType() {
        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/InvalidResource")
        .then()
            .statusCode(404);
    }

    // ==================== HAPI FHIR Client Tests ====================

    @Test
    @Order(90)
    void testHapiFhirClientCreate() {
        Patient patient = new Patient();
        patient.addName().setFamily("HapiClient").addGiven("Test");
        patient.setActive(true);

        MethodOutcome outcome = fhirClient.create()
            .resource(patient)
            .execute();

        assertTrue(outcome.getCreated());
        assertNotNull(outcome.getId());
    }

    @Test
    @Order(91)
    void testHapiFhirClientSearch() {
        Bundle bundle = fhirClient.search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .execute();

        assertNotNull(bundle);
        assertEquals("Bundle", bundle.getResourceType().name());
    }
}
