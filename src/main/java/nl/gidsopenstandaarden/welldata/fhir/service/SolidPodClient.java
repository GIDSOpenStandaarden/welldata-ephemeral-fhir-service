package nl.gidsopenstandaarden.welldata.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for communicating with a Solid pod to load and persist FHIR resources.
 *
 * The pod stores FHIR resources as RDF/Turtle files in a specific structure:
 * <pod>/weare/fhir/Patient/<uuid>.ttl
 * <pod>/weare/fhir/Observation/<uuid>.ttl
 * etc.
 */
@Service
public class SolidPodClient {

    private static final Logger log = LoggerFactory.getLogger(SolidPodClient.class);
    private static final String LDP_CONTAINS = "http://www.w3.org/ns/ldp#contains";

    private final FhirContext fhirContext;
    private final HttpClient httpClient;

    @Value("${welldata.solid.enabled:false}")
    private boolean solidEnabled;

    @Value("${welldata.solid.fhir-container-path:/weare/fhir}")
    private String fhirContainerPath;

    public SolidPodClient(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public boolean isEnabled() {
        return solidEnabled;
    }

    /**
     * Loads all resources of a given type from the pod.
     *
     * @param resourceType The FHIR resource type (e.g., "Patient", "Observation")
     * @param accessToken  The Bearer token for authentication
     * @return List of loaded FHIR resources
     */
    public <T extends DomainResource> List<T> loadResources(String resourceType, String accessToken, Class<T> clazz) {
        List<T> resources = new ArrayList<>();

        if (!solidEnabled) {
            log.debug("Solid pod integration disabled, skipping resource loading");
            return resources;
        }

        String containerUrl = buildContainerUrl(resourceType);
        log.debug("Loading {} resources from {}", resourceType, containerUrl);

        try {
            // First, list all resources in the container
            List<String> resourceUrls = listContainerContents(containerUrl, accessToken);
            log.debug("Found {} {} resources in pod", resourceUrls.size(), resourceType);

            // Load each resource
            for (String resourceUrl : resourceUrls) {
                if (resourceUrl.endsWith(".ttl")) {
                    try {
                        T resource = loadResource(resourceUrl, accessToken, clazz);
                        if (resource != null) {
                            resources.add(resource);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to load resource from {}: {}", resourceUrl, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to load {} resources from pod: {}", resourceType, e.getMessage(), e);
        }

        return resources;
    }

    /**
     * Lists the contents of an LDP container.
     */
    private List<String> listContainerContents(String containerUrl, String accessToken) throws IOException, InterruptedException {
        List<String> contents = new ArrayList<>();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(containerUrl))
                .header("Accept", "text/turtle")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Model model = ModelFactory.createDefaultModel();
            model.read(new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)), containerUrl, "TURTLE");

            // Find all ldp:contains triples
            StmtIterator iter = model.listStatements(null, model.createProperty(LDP_CONTAINS), (Resource) null);
            while (iter.hasNext()) {
                String resourceUri = iter.nextStatement().getObject().asResource().getURI();
                contents.add(resourceUri);
            }
        } else if (response.statusCode() == 404) {
            log.debug("Container {} does not exist yet", containerUrl);
        } else {
            log.warn("Failed to list container {}: {} {}", containerUrl, response.statusCode(), response.body());
        }

        return contents;
    }

    /**
     * Loads a single FHIR resource from the pod.
     */
    private <T extends DomainResource> T loadResource(String resourceUrl, String accessToken, Class<T> clazz) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resourceUrl))
                .header("Accept", "text/turtle")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseTurtleToFhir(response.body(), resourceUrl, clazz);
        } else {
            log.warn("Failed to load resource {}: {}", resourceUrl, response.statusCode());
            return null;
        }
    }

    /**
     * Saves a FHIR resource to the pod.
     *
     * @param resource    The FHIR resource to save
     * @param accessToken The Bearer token for authentication
     */
    public void saveResource(DomainResource resource, String accessToken) {
        if (!solidEnabled) {
            log.debug("Solid pod integration disabled, skipping resource save");
            return;
        }

        String resourceType = resource.fhirType();
        String resourceId = resource.getIdElement().getIdPart();
        String resourceUrl = buildResourceUrl(resourceType, resourceId);

        log.debug("Saving {} {} to {}", resourceType, resourceId, resourceUrl);

        try {
            String turtle = convertFhirToTurtle(resource);
            if (log.isDebugEnabled()) {
                log.debug("Turtle content for {} {} ({} bytes):\n{}", resourceType, resourceId, turtle.length(), turtle);
            }

            // Validate the Turtle syntax before sending
            try {
                Model model = ModelFactory.createDefaultModel();
                model.read(new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)), null, "TURTLE");
                log.debug("Turtle validation passed for {} {}", resourceType, resourceId);
            } catch (Exception e) {
                log.error("Invalid Turtle generated for {} {}: {}\nContent:\n{}", resourceType, resourceId, e.getMessage(), turtle);
                throw new RuntimeException("Invalid Turtle generated: " + e.getMessage(), e);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resourceUrl))
                    .header("Content-Type", "text/turtle")
                    .header("Authorization", "Bearer " + accessToken)
                    .PUT(HttpRequest.BodyPublishers.ofString(turtle))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Successfully saved {} to pod", resourceUrl);
            } else {
                log.error("Failed to save resource to pod: {} {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to save resource to pod: " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error saving resource to pod: {}", e.getMessage(), e);
            throw new RuntimeException("Error saving resource to pod", e);
        }
    }

    /**
     * Deletes a FHIR resource from the pod.
     *
     * @param resourceType The FHIR resource type
     * @param resourceId   The resource ID
     * @param accessToken  The Bearer token for authentication
     */
    public void deleteResource(String resourceType, String resourceId, String accessToken) {
        if (!solidEnabled) {
            log.debug("Solid pod integration disabled, skipping resource delete");
            return;
        }

        String resourceUrl = buildResourceUrl(resourceType, resourceId);
        log.debug("Deleting {} from pod", resourceUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resourceUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300 || response.statusCode() == 404) {
                log.debug("Successfully deleted {} from pod", resourceUrl);
            } else {
                log.error("Failed to delete resource from pod: {} {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to delete resource from pod: " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error deleting resource from pod: {}", e.getMessage(), e);
            throw new RuntimeException("Error deleting resource from pod", e);
        }
    }

    /**
     * Ensures the container structure exists in the pod.
     * Uses the WebID from AccessTokenContext to determine the pod URL.
     */
    public void ensureContainerStructure(String accessToken) {
        if (!solidEnabled) {
            return;
        }

        String podBaseUrl = getPodBaseUrl();
        if (podBaseUrl == null) {
            log.warn("Cannot ensure container structure - no WebID available");
            return;
        }

        log.info("Ensuring pod container structure exists at {}...", podBaseUrl);

        try {
            // Create /weare/
            createContainerIfNotExists(podBaseUrl + "/weare/", "WellData Health Data", accessToken);

            // Create /weare/fhir/
            createContainerIfNotExists(podBaseUrl + fhirContainerPath + "/", "FHIR Resources", accessToken);

            // Create resource type containers (excluding Questionnaire as it's served locally)
            for (String resourceType : List.of("Patient", "Observation", "QuestionnaireResponse")) {
                createContainerIfNotExists(buildContainerUrl(resourceType), resourceType + " Resources", accessToken);
            }

            log.info("Pod container structure ready");

        } catch (Exception e) {
            log.error("Failed to ensure container structure: {}", e.getMessage(), e);
        }
    }

    private void createContainerIfNotExists(String containerUrl, String title, String accessToken) throws IOException, InterruptedException {
        // Check if container exists
        HttpRequest checkRequest = HttpRequest.newBuilder()
                .uri(URI.create(containerUrl))
                .header("Authorization", "Bearer " + accessToken)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> checkResponse = httpClient.send(checkRequest, HttpResponse.BodyHandlers.discarding());

        if (checkResponse.statusCode() == 404) {
            // Create the container
            String turtle = String.format("""
                    @prefix ldp: <http://www.w3.org/ns/ldp#> .
                    @prefix dcterms: <http://purl.org/dc/terms/> .
                    <> a ldp:BasicContainer ;
                       dcterms:title "%s" .
                    """, title);

            HttpRequest createRequest = HttpRequest.newBuilder()
                    .uri(URI.create(containerUrl))
                    .header("Content-Type", "text/turtle")
                    .header("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"")
                    .header("Authorization", "Bearer " + accessToken)
                    .PUT(HttpRequest.BodyPublishers.ofString(turtle))
                    .build();

            HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

            if (createResponse.statusCode() >= 200 && createResponse.statusCode() < 300) {
                log.debug("Created container: {}", containerUrl);
            } else {
                log.warn("Failed to create container {}: {} {}", containerUrl, createResponse.statusCode(), createResponse.body());
            }
        }
    }

    /**
     * Derives the pod base URL from the user's WebID.
     * WebID format: https://pod-host/profile/card#me -> pod URL: https://pod-host
     *
     * @return The pod base URL, or null if no WebID is available
     */
    private String getPodBaseUrl() {
        AccessTokenContext context = AccessTokenContext.get();
        if (context == null || context.getSubject() == null) {
            log.warn("No access token context or subject (WebID) available");
            return null;
        }

        String webId = context.getSubject();
        try {
            URI webIdUri = URI.create(webId);
            String podBaseUrl = webIdUri.getScheme() + "://" + webIdUri.getHost();
            if (webIdUri.getPort() != -1) {
                podBaseUrl += ":" + webIdUri.getPort();
            }
            log.debug("Derived pod base URL {} from WebID {}", podBaseUrl, webId);
            return podBaseUrl;
        } catch (Exception e) {
            log.error("Failed to parse WebID {}: {}", webId, e.getMessage());
            return null;
        }
    }

    private String buildContainerUrl(String resourceType) {
        String podBaseUrl = getPodBaseUrl();
        if (podBaseUrl == null) {
            throw new IllegalStateException("Cannot determine pod URL - no WebID available in access token");
        }
        return podBaseUrl + fhirContainerPath + "/" + resourceType + "/";
    }

    private String buildResourceUrl(String resourceType, String resourceId) {
        return buildContainerUrl(resourceType) + resourceId + ".ttl";
    }

    /**
     * Converts a FHIR resource to RDF/Turtle format.
     */
    private String convertFhirToTurtle(DomainResource resource) {
        IParser rdfParser = fhirContext.newRDFParser();
        return rdfParser.encodeResourceToString(resource);
    }

    /**
     * Parses an RDF/Turtle string to a FHIR resource.
     */
    @SuppressWarnings("unchecked")
    private <T extends DomainResource> T parseTurtleToFhir(String turtle, String baseUri, Class<T> clazz) {
        try {
            IParser rdfParser = fhirContext.newRDFParser();
            return (T) rdfParser.parseResource(clazz, turtle);
        } catch (Exception e) {
            log.warn("Failed to parse Turtle to FHIR: {}", e.getMessage());
            return null;
        }
    }
}
