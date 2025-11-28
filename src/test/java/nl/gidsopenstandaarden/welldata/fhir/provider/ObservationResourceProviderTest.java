package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import nl.gidsopenstandaarden.welldata.fhir.service.SolidPodClient;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ObservationResourceProvider search functionality.
 */
class ObservationResourceProviderTest {

    private FhirContext fhirContext;
    private SessionManager sessionManager;
    private SolidPodClient solidPodClient;
    private ObservationResourceProvider provider;

    private static final String SESSION_KEY = "test-session";

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        sessionManager = new SessionManager();
        solidPodClient = mock(SolidPodClient.class);
        when(solidPodClient.isEnabled()).thenReturn(false);

        provider = new ObservationResourceProvider(fhirContext, sessionManager, solidPodClient);

        // Set up access token context
        AccessTokenContext context = new AccessTokenContext(
            "test-token", SESSION_KEY, "https://pod.example.com/user#me",
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
        AccessTokenContext.set(context);
        sessionManager.getOrCreateSession(SESSION_KEY);

        // Create test observations
        createTestObservations();
    }

    @AfterEach
    void tearDown() {
        AccessTokenContext.clear();
    }

    private void createTestObservations() {
        // Weight observation for Patient/1
        Observation weight = new Observation();
        weight.setId("obs-weight");
        weight.setStatus(Observation.ObservationStatus.FINAL);
        weight.setSubject(new Reference("Patient/1"));
        weight.setCode(new CodeableConcept().addCoding(
            new Coding("http://snomed.info/sct", "27113001", "Body weight")
        ));
        weight.addCategory(new CodeableConcept().addCoding(
            new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")
        ));
        weight.setEffective(new DateTimeType(new Date()));
        weight.setValue(new Quantity().setValue(75).setUnit("kg"));
        provider.store(weight);

        // BMI observation for Patient/1
        Observation bmi = new Observation();
        bmi.setId("obs-bmi");
        bmi.setStatus(Observation.ObservationStatus.FINAL);
        bmi.setSubject(new Reference("Patient/1"));
        bmi.setCode(new CodeableConcept().addCoding(
            new Coding("http://snomed.info/sct", "60621009", "BMI")
        ));
        bmi.addCategory(new CodeableConcept().addCoding(
            new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")
        ));
        bmi.setEffective(new DateTimeType(new Date()));
        bmi.setValue(new Quantity().setValue(24.5).setUnit("kg/m2"));
        provider.store(bmi);

        // Cholesterol observation for Patient/2 (different patient)
        Observation cholesterol = new Observation();
        cholesterol.setId("obs-cholesterol");
        cholesterol.setStatus(Observation.ObservationStatus.PRELIMINARY);
        cholesterol.setSubject(new Reference("Patient/2"));
        cholesterol.setCode(new CodeableConcept().addCoding(
            new Coding("http://snomed.info/sct", "121868005", "Total cholesterol")
        ));
        cholesterol.addCategory(new CodeableConcept().addCoding(
            new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "laboratory", "Laboratory")
        ));
        cholesterol.setEffective(new DateTimeType(new Date()));
        cholesterol.setValue(new Quantity().setValue(5.2).setUnit("mmol/L"));
        provider.store(cholesterol);
    }

    @Test
    void testSearchAllObservations() {
        IBundleProvider results = provider.searchObservations(null, null, null, null, null, null);

        assertEquals(3, results.size());
    }

    @Test
    void testSearchBySubject() {
        ReferenceParam subject = new ReferenceParam("Patient/1");
        IBundleProvider results = provider.searchObservations(subject, null, null, null, null, null);

        assertEquals(2, results.size());
    }

    @Test
    void testSearchBySubjectIdOnly() {
        ReferenceParam subject = new ReferenceParam("1");
        IBundleProvider results = provider.searchObservations(subject, null, null, null, null, null);

        assertEquals(2, results.size());
    }

    @Test
    void testSearchByCode() {
        TokenParam code = new TokenParam("27113001"); // Weight
        IBundleProvider results = provider.searchObservations(null, code, null, null, null, null);

        assertEquals(1, results.size());
        Observation obs = (Observation) results.getResources(0, 1).get(0);
        assertEquals("27113001", obs.getCode().getCodingFirstRep().getCode());
    }

    @Test
    void testSearchByCodeWithSystem() {
        TokenParam code = new TokenParam("http://snomed.info/sct", "60621009"); // BMI
        IBundleProvider results = provider.searchObservations(null, code, null, null, null, null);

        assertEquals(1, results.size());
    }

    @Test
    void testSearchByCodeWithWrongSystem() {
        TokenParam code = new TokenParam("http://wrong.system", "27113001");
        IBundleProvider results = provider.searchObservations(null, code, null, null, null, null);

        assertEquals(0, results.size());
    }

    @Test
    void testSearchByStatus() {
        TokenParam status = new TokenParam("final");
        IBundleProvider results = provider.searchObservations(null, null, null, status, null, null);

        assertEquals(2, results.size());
    }

    @Test
    void testSearchByPreliminaryStatus() {
        TokenParam status = new TokenParam("preliminary");
        IBundleProvider results = provider.searchObservations(null, null, null, status, null, null);

        assertEquals(1, results.size());
    }

    @Test
    void testSearchByCategory() {
        TokenParam category = new TokenParam("vital-signs");
        IBundleProvider results = provider.searchObservations(null, null, null, null, category, null);

        assertEquals(2, results.size());
    }

    @Test
    void testSearchByCategoryWithSystem() {
        TokenParam category = new TokenParam(
            "http://terminology.hl7.org/CodeSystem/observation-category", "laboratory"
        );
        IBundleProvider results = provider.searchObservations(null, null, null, null, category, null);

        assertEquals(1, results.size());
    }

    @Test
    void testSearchCombinedCriteria() {
        ReferenceParam subject = new ReferenceParam("Patient/1");
        TokenParam category = new TokenParam("vital-signs");
        TokenParam status = new TokenParam("final");

        IBundleProvider results = provider.searchObservations(subject, null, null, status, category, null);

        assertEquals(2, results.size());
    }

    @Test
    void testSearchNoResults() {
        ReferenceParam subject = new ReferenceParam("Patient/nonexistent");
        IBundleProvider results = provider.searchObservations(subject, null, null, null, null, null);

        assertEquals(0, results.size());
    }

    @Test
    void testSearchByDateRange() {
        // Create an old observation
        Observation oldObs = new Observation();
        oldObs.setId("obs-old");
        oldObs.setStatus(Observation.ObservationStatus.FINAL);
        oldObs.setSubject(new Reference("Patient/1"));
        oldObs.setCode(new CodeableConcept().addCoding(
            new Coding("http://snomed.info/sct", "27113001", "Body weight")
        ));
        // Set date to 30 days ago
        Date oldDate = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        oldObs.setEffective(new DateTimeType(oldDate));
        provider.store(oldObs);

        // Search for observations in the last 7 days
        Date weekAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
        DateRangeParam dateRange = new DateRangeParam(weekAgo, new Date());

        IBundleProvider results = provider.searchObservations(null, null, dateRange, null, null, null);

        // Should only find the 3 recent observations, not the old one
        assertEquals(3, results.size());
    }

    @Test
    void testInheritedCrudOperations() {
        // Test that CRUD operations from parent class work
        Observation newObs = new Observation();
        newObs.setStatus(Observation.ObservationStatus.FINAL);
        newObs.setCode(new CodeableConcept().addCoding(
            new Coding("http://snomed.info/sct", "50373000", "Body height")
        ));

        var createResult = provider.create(newObs, null);
        assertTrue(createResult.getCreated());

        String id = createResult.getId().getIdPart();
        Observation retrieved = provider.read(new IdType("Observation", id), null);
        assertNotNull(retrieved);
        assertEquals("50373000", retrieved.getCode().getCodingFirstRep().getCode());
    }
}
