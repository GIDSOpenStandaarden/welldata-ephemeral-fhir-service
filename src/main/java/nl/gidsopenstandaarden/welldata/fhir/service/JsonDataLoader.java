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

/**
 * Service to load FHIR resources from JSON files into a session.
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
     * Load all resources from JSON files into the providers for a specific session.
     */
    public void loadResources(
            SessionManager.Session session,
            PatientResourceProvider patientProvider,
            ObservationResourceProvider observationProvider,
            QuestionnaireResourceProvider questionnaireProvider,
            QuestionnaireResponseResourceProvider questionnaireResponseProvider) {

        if (session.isDataLoaded()) {
            LOG.debug("Session {} already has data loaded, skipping", session.getSessionKey());
            return;
        }

        LOG.info("Loading resources for session {}...", session.getSessionKey());

        int patientCount = loadResourcesOfType("Patient", Patient.class, patientProvider, session);
        int observationCount = loadResourcesOfType("Observation", Observation.class, observationProvider, session);
        int questionnaireCount = loadResourcesOfType("Questionnaire", Questionnaire.class, questionnaireProvider, session);
        int qrCount = loadResourcesOfType("QuestionnaireResponse", QuestionnaireResponse.class, questionnaireResponseProvider, session);

        session.setDataLoaded(true);

        LOG.info("Loaded {} Patients, {} Observations, {} Questionnaires, {} QuestionnaireResponses for session {}",
                patientCount, observationCount, questionnaireCount, qrCount, session.getSessionKey());
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
