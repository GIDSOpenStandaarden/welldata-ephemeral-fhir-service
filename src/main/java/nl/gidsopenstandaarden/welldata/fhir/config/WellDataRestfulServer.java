package nl.gidsopenstandaarden.welldata.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import nl.gidsopenstandaarden.welldata.fhir.interceptor.AccessTokenInterceptor;
import nl.gidsopenstandaarden.welldata.fhir.provider.*;
import nl.gidsopenstandaarden.welldata.fhir.service.IgPackageLoader;
import nl.gidsopenstandaarden.welldata.fhir.service.JsonDataLoader;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import nl.gidsopenstandaarden.welldata.fhir.service.SolidPodClient;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

import jakarta.servlet.ServletException;
import java.util.Arrays;

/**
 * WellData FHIR RESTful Server configuration.
 *
 * This server provides an in-memory FHIR R4 API for WellData resources.
 * Resources are scoped per access token session and loaded on-demand.
 */
@Component
public class WellDataRestfulServer extends RestfulServer {

    private final JsonDataLoader jsonDataLoader;
    private final IgPackageLoader igPackageLoader;
    private final SessionManager sessionManager;
    private final SolidPodClient solidPodClient;

    // Session-scoped resource providers (require authentication)
    private final PatientResourceProvider patientProvider;
    private final ObservationResourceProvider observationProvider;
    private final QuestionnaireResponseResourceProvider questionnaireResponseProvider;

    // Static resource providers (no authentication required)
    private final StaticQuestionnaireResourceProvider questionnaireProvider;
    private final StructureDefinitionResourceProvider structureDefinitionProvider;
    private final ImplementationGuideResourceProvider implementationGuideProvider;

    public WellDataRestfulServer(JsonDataLoader jsonDataLoader, IgPackageLoader igPackageLoader,
                                  SessionManager sessionManager, SolidPodClient solidPodClient) {
        super(FhirContext.forR4());
        this.jsonDataLoader = jsonDataLoader;
        this.igPackageLoader = igPackageLoader;
        this.sessionManager = sessionManager;
        this.solidPodClient = solidPodClient;

        FhirContext ctx = getFhirContext();
        // Session-scoped providers
        this.patientProvider = new PatientResourceProvider(ctx, sessionManager, solidPodClient);
        this.observationProvider = new ObservationResourceProvider(ctx, sessionManager, solidPodClient);
        this.questionnaireResponseProvider = new QuestionnaireResponseResourceProvider(ctx, sessionManager, solidPodClient);

        // Static providers (shared across all sessions)
        this.questionnaireProvider = new StaticQuestionnaireResourceProvider();
        this.structureDefinitionProvider = new StructureDefinitionResourceProvider();
        this.implementationGuideProvider = new ImplementationGuideResourceProvider();
    }

    @Override
    protected void initialize() throws ServletException {
        // Register resource providers
        registerProvider(patientProvider);
        registerProvider(observationProvider);
        registerProvider(questionnaireProvider);
        registerProvider(questionnaireResponseProvider);
        registerProvider(structureDefinitionProvider);
        registerProvider(implementationGuideProvider);

        // Add interceptors
        registerInterceptor(new ResponseHighlighterInterceptor());
        registerInterceptor(buildCorsInterceptor());

        // Create and configure the access token interceptor
        AccessTokenInterceptor accessTokenInterceptor = new AccessTokenInterceptor(sessionManager);
        accessTokenInterceptor.setSessionInitializer(this::loadSessionData);
        registerInterceptor(accessTokenInterceptor);

        // Set server metadata
        setServerName("WellData Ephemeral FHIR Server");
        setServerVersion("0.1.0");

        // Load IG package (profiles and implementation guides) - these are shared across sessions
        loadIgPackage();

        // Load static Questionnaires from local files - these are shared across sessions
        loadQuestionnaires();
    }

    private CorsInterceptor buildCorsInterceptor() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedHeader("*");
        config.addAllowedOrigin("*");
        config.addExposedHeader("Location");
        config.addExposedHeader("Content-Location");
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        return new CorsInterceptor(config);
    }

    private void loadIgPackage() {
        igPackageLoader.loadIgPackage(structureDefinitionProvider, implementationGuideProvider);
    }

    private void loadQuestionnaires() {
        jsonDataLoader.loadQuestionnaires(questionnaireProvider);
    }

    /**
     * Load initial data into a specific session.
     * Called by the interceptor when a new session is created.
     *
     * If Solid pod integration is enabled, data is loaded from the pod.
     * Otherwise, test data is loaded from the classpath (if enabled).
     *
     * Note: Questionnaires are loaded statically at startup and are not session-scoped.
     */
    public void loadSessionData(SessionManager.Session session) {
        if (solidPodClient.isEnabled()) {
            // Load data from Solid pod
            loadFromSolidPod(session);
        } else {
            // Load test data from classpath
            jsonDataLoader.loadSessionResources(session, patientProvider, observationProvider,
                                                questionnaireResponseProvider);
        }
    }

    /**
     * Load resources from the Solid pod into the session.
     * Note: Questionnaires are loaded statically at startup and are not session-scoped.
     */
    private void loadFromSolidPod(SessionManager.Session session) {
        nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext context =
            nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext.get();

        if (context == null) {
            return;
        }

        String accessToken = context.getToken();

        // Load each resource type from the pod (Questionnaires are static, not per-session)
        solidPodClient.loadResources("Patient", accessToken, org.hl7.fhir.r4.model.Patient.class)
            .forEach(p -> patientProvider.store(p, session));

        solidPodClient.loadResources("Observation", accessToken, org.hl7.fhir.r4.model.Observation.class)
            .forEach(o -> observationProvider.store(o, session));

        solidPodClient.loadResources("QuestionnaireResponse", accessToken, org.hl7.fhir.r4.model.QuestionnaireResponse.class)
            .forEach(qr -> questionnaireResponseProvider.store(qr, session));

        session.setDataLoaded(true);
    }

    // Getters for providers (useful for testing and management)
    public PatientResourceProvider getPatientProvider() {
        return patientProvider;
    }

    public ObservationResourceProvider getObservationProvider() {
        return observationProvider;
    }

    public StaticQuestionnaireResourceProvider getQuestionnaireProvider() {
        return questionnaireProvider;
    }

    public QuestionnaireResponseResourceProvider getQuestionnaireResponseProvider() {
        return questionnaireResponseProvider;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public JsonDataLoader getJsonDataLoader() {
        return jsonDataLoader;
    }

    public SolidPodClient getSolidPodClient() {
        return solidPodClient;
    }
}
