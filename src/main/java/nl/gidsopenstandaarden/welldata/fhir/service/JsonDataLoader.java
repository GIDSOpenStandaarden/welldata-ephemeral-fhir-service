package nl.gidsopenstandaarden.welldata.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import nl.gidsopenstandaarden.welldata.fhir.provider.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Service to load FHIR resources from JSON files.
 *
 * This loader reads JSON files from the classpath and converts them to FHIR resources
 * using the HAPI FHIR JSON parser.
 */
@Service
public class JsonDataLoader {

    private static final Logger LOG = LoggerFactory.getLogger(JsonDataLoader.class);

    private final FhirContext fhirContext;
    private final IParser jsonParser;

    @Value("${welldata.testdata.path:classpath:testdata}")
    private String testdataPath;

    public JsonDataLoader() {
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser();
    }

    /**
     * Load static Questionnaires from JSON files (shared across all sessions).
     */
    public void loadQuestionnaires(StaticQuestionnaireResourceProvider questionnaireProvider) {
        LOG.info("Loading static Questionnaires...");
        int count = loadStaticResources("Questionnaire", Questionnaire.class, questionnaireProvider::store);
        LOG.info("Loaded {} static Questionnaires", count);
    }

    /**
     * Load session-scoped resources from JSON files into the providers for a specific session.
     * Note: Questionnaires are loaded statically at startup and are not included here.
     */
    public void loadSessionResources(
            SessionManager.Session session,
            PatientResourceProvider patientProvider,
            ObservationResourceProvider observationProvider,
            QuestionnaireResponseResourceProvider questionnaireResponseProvider) {

        if (session.isDataLoaded()) {
            LOG.debug("Session {} already has data loaded, skipping", session.getSessionKey());
            return;
        }

        LOG.info("Loading resources for session {}...", session.getSessionKey());

        int patientCount = loadResourcesOfType("Patient", Patient.class, patientProvider, session);
        int observationCount = loadResourcesOfType("Observation", Observation.class, observationProvider, session);
        int qrCount = loadResourcesOfType("QuestionnaireResponse", QuestionnaireResponse.class, questionnaireResponseProvider, session);

        session.setDataLoaded(true);

        LOG.info("Loaded {} Patients, {} Observations, {} QuestionnaireResponses for session {}",
                patientCount, observationCount, qrCount, session.getSessionKey());
    }

    /**
     * Load static resources (not session-scoped) using a consumer.
     */
    private <T extends org.hl7.fhir.instance.model.api.IBaseResource> int loadStaticResources(
            String resourceType,
            Class<T> resourceClass,
            Consumer<T> storeFunction) {

        int count = 0;
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            String pattern = "classpath:testdata/" + resourceType + "/*.json";
            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources) {
                try {
                    T fhirResource = loadResource(resource, resourceClass);
                    if (fhirResource != null) {
                        storeFunction.accept(fhirResource);
                        count++;
                        LOG.debug("Loaded static {} from {}", resourceType, resource.getFilename());
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to load {} from {}: {}", resourceType, resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to find {} resources: {}", resourceType, e.getMessage());
        }

        return count;
    }

    private <T extends org.hl7.fhir.instance.model.api.IBaseResource> int loadResourcesOfType(
            String resourceType,
            Class<T> resourceClass,
            WellDataResourceProvider<T> provider,
            SessionManager.Session session) {

        int count = 0;
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            String pattern = "classpath:testdata/" + resourceType + "/*.json";
            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources) {
                try {
                    T fhirResource = loadResource(resource, resourceClass);
                    if (fhirResource != null) {
                        provider.store(fhirResource, session);
                        count++;
                        LOG.debug("Loaded {} from {} into session {}", resourceType, resource.getFilename(), session.getSessionKey());
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to load {} from {}: {}", resourceType, resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to find {} resources: {}", resourceType, e.getMessage());
        }

        return count;
    }

    private <T extends org.hl7.fhir.instance.model.api.IBaseResource> T loadResource(
            Resource resource,
            Class<T> resourceClass) throws IOException {

        try (InputStream is = resource.getInputStream()) {
            String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Parse JSON content using HAPI FHIR JSON parser
            @SuppressWarnings("unchecked")
            T parsed = (T) jsonParser.parseResource(resourceClass, jsonContent);

            return parsed;
        }
    }

    /**
     * Load a single resource from JSON content string.
     */
    public <T extends org.hl7.fhir.instance.model.api.IBaseResource> T parseResource(
            String jsonContent,
            Class<T> resourceClass) {

        @SuppressWarnings("unchecked")
        T parsed = (T) jsonParser.parseResource(resourceClass, jsonContent);
        return parsed;
    }

    /**
     * Convert a FHIR resource to JSON format.
     */
    public String toJson(org.hl7.fhir.instance.model.api.IBaseResource resource) {
        return jsonParser.encodeResourceToString(resource);
    }
}
