package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import nl.gidsopenstandaarden.welldata.fhir.service.SolidPodClient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory resource provider for WellData FHIR resources.
 * Resources are scoped to the current access token session.
 *
 * This provider stores resources in memory per session and will later be extended
 * to sync with Solid pods.
 *
 * @param <T> The resource type this provider handles
 */
public class WellDataResourceProvider<T extends IBaseResource> implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(WellDataResourceProvider.class);

    private final Class<T> resourceType;
    private final FhirContext fhirContext;
    private final String resourceName;
    private final SessionManager sessionManager;
    private final SolidPodClient solidPodClient;

    public WellDataResourceProvider(FhirContext fhirContext, Class<T> resourceType, SessionManager sessionManager, SolidPodClient solidPodClient) {
        this.fhirContext = fhirContext;
        this.resourceType = resourceType;
        this.resourceName = fhirContext.getResourceType(resourceType);
        this.sessionManager = sessionManager;
        this.solidPodClient = solidPodClient;
    }

    @Override
    public Class<T> getResourceType() {
        return resourceType;
    }

    /**
     * Get the current session, throwing an exception if not authenticated.
     */
    private SessionManager.Session requireSession() {
        SessionManager.Session session = sessionManager.getCurrentSession();
        if (session == null) {
            throw new AuthenticationException("No valid session - authentication required");
        }
        return session;
    }

    /**
     * Store a resource in a specific session (used for initial data loading).
     */
    public void store(T resource, SessionManager.Session session) {
        IIdType id = resource.getIdElement();
        String idPart = id.getIdPart();

        if (idPart == null || idPart.isEmpty()) {
            idPart = String.valueOf(session.getAndIncrementNextId(resourceName));
        }

        long version = 1L;
        if (id.hasVersionIdPart()) {
            version = id.getVersionIdPartAsLong();
        }

        // Clone the resource to avoid external modifications
        @SuppressWarnings("unchecked")
        T cloned = (T) fhirContext.newTerser().clone(resource);

        // Set the ID with version
        IIdType newId = new IdType(resourceName, idPart, String.valueOf(version));
        cloned.setId(newId);

        // Update meta
        if (cloned instanceof Resource r) {
            if (r.getMeta() == null) {
                r.setMeta(new Meta());
            }
            r.getMeta().setVersionId(String.valueOf(version));
            r.getMeta().setLastUpdated(new Date());
        }

        // Store in session
        session.storeResource(resourceName, idPart, version, cloned);

        LOG.debug("Stored {}/{} version {} in session {}", resourceName, idPart, version, session.getSessionKey());
    }

    /**
     * Store a resource in the current session.
     */
    public void store(T resource) {
        store(resource, requireSession());
    }

    @Read
    public T read(@IdParam IIdType theId, RequestDetails requestDetails) {
        SessionManager.Session session = requireSession();
        String idPart = theId.getIdPart();

        if (session.isDeleted(resourceName, idPart)) {
            throw new ResourceGoneException(theId);
        }

        Long version = theId.hasVersionIdPart() ? theId.getVersionIdPartAsLong() : null;
        T resource = session.getResource(resourceName, idPart, version);

        if (resource == null) {
            throw new ResourceNotFoundException(theId);
        }

        @SuppressWarnings("unchecked")
        T cloned = (T) fhirContext.newTerser().clone(resource);
        return cloned;
    }

    @Create
    public MethodOutcome create(@ResourceParam T resource, RequestDetails requestDetails) {
        SessionManager.Session session = requireSession();
        String id = String.valueOf(session.getAndIncrementNextId(resourceName));

        @SuppressWarnings("unchecked")
        T cloned = (T) fhirContext.newTerser().clone(resource);

        IIdType newId = new IdType(resourceName, id, "1");
        cloned.setId(newId);

        if (cloned instanceof Resource r) {
            if (r.getMeta() == null) {
                r.setMeta(new Meta());
            }
            r.getMeta().setVersionId("1");
            r.getMeta().setLastUpdated(new Date());
        }

        session.storeResource(resourceName, id, 1L, cloned);

        // Persist to Solid pod (write-through)
        persistToPod(cloned);

        LOG.info("Created {}/{} in session {}", resourceName, id, session.getSessionKey());

        return new MethodOutcome()
                .setCreated(true)
                .setId(newId)
                .setResource(cloned);
    }

    @Update
    public MethodOutcome update(@IdParam IIdType theId, @ResourceParam T resource, RequestDetails requestDetails) {
        SessionManager.Session session = requireSession();
        String idPart = theId.getIdPart();

        // Determine new version
        T existing = session.getResource(resourceName, idPart, null);
        long newVersion;
        if (existing == null) {
            newVersion = 1L;
        } else {
            newVersion = existing.getIdElement().getVersionIdPartAsLong() + 1;
        }

        @SuppressWarnings("unchecked")
        T cloned = (T) fhirContext.newTerser().clone(resource);

        IIdType newId = new IdType(resourceName, idPart, String.valueOf(newVersion));
        cloned.setId(newId);

        if (cloned instanceof Resource r) {
            if (r.getMeta() == null) {
                r.setMeta(new Meta());
            }
            r.getMeta().setVersionId(String.valueOf(newVersion));
            r.getMeta().setLastUpdated(new Date());
        }

        session.storeResource(resourceName, idPart, newVersion, cloned);

        // Persist to Solid pod (write-through)
        persistToPod(cloned);

        LOG.info("Updated {}/{} to version {} in session {}", resourceName, idPart, newVersion, session.getSessionKey());

        return new MethodOutcome()
                .setId(newId)
                .setResource(cloned);
    }

    @Delete
    public MethodOutcome delete(@IdParam IIdType theId, RequestDetails requestDetails) {
        SessionManager.Session session = requireSession();
        String idPart = theId.getIdPart();

        if (!session.resourceExists(resourceName, idPart)) {
            throw new ResourceNotFoundException(theId);
        }

        session.deleteResource(resourceName, idPart);

        // Delete from Solid pod
        deleteFromPod(idPart);

        LOG.info("Deleted {}/{} in session {}", resourceName, idPart, session.getSessionKey());

        return new MethodOutcome().setId(theId);
    }

    @Search
    public IBundleProvider search(RequestDetails requestDetails) {
        SessionManager.Session session = requireSession();
        List<T> results = session.getAllResources(resourceName);

        // Clone all resources
        List<T> cloned = results.stream()
                .map(r -> {
                    @SuppressWarnings("unchecked")
                    T c = (T) fhirContext.newTerser().clone(r);
                    return c;
                })
                .collect(Collectors.toList());

        return new SimpleBundleProvider(cloned);
    }

    @Search
    public IBundleProvider searchById(
            @RequiredParam(name = Resource.SP_RES_ID) TokenParam id,
            RequestDetails requestDetails) {

        String idPart = id.getValue();
        try {
            T resource = read(new IdType(resourceName, idPart), requestDetails);
            return new SimpleBundleProvider(Collections.singletonList(resource));
        } catch (ResourceNotFoundException | ResourceGoneException e) {
            return new SimpleBundleProvider(Collections.emptyList());
        }
    }

    /**
     * Get all non-deleted resources (latest versions) from the current session.
     */
    public List<T> getAllResources() {
        SessionManager.Session session = sessionManager.getCurrentSession();
        if (session == null) {
            return Collections.emptyList();
        }

        List<T> results = session.getAllResources(resourceName);
        return results.stream()
                .map(r -> {
                    @SuppressWarnings("unchecked")
                    T cloned = (T) fhirContext.newTerser().clone(r);
                    return cloned;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get the count of stored resources in the current session.
     */
    public int getResourceCount() {
        SessionManager.Session session = sessionManager.getCurrentSession();
        if (session == null) {
            return 0;
        }
        return session.getAllResources(resourceName).size();
    }

    /**
     * Clear all stored resources in the current session.
     */
    public void clear() {
        SessionManager.Session session = sessionManager.getCurrentSession();
        if (session != null) {
            session.getResourceMap(resourceName).clear();
            session.getDeletedIds(resourceName).clear();
            LOG.info("Cleared all {} resources in session {}", resourceName, session.getSessionKey());
        }
    }

    /**
     * Persist a resource to the Solid pod (write-through).
     */
    @SuppressWarnings("unchecked")
    private void persistToPod(T resource) {
        if (solidPodClient != null && solidPodClient.isEnabled()) {
            AccessTokenContext context = AccessTokenContext.get();
            if (context != null && resource instanceof DomainResource dr) {
                try {
                    solidPodClient.saveResource(dr, context.getToken());
                } catch (Exception e) {
                    LOG.error("Failed to persist resource to Solid pod: {}", e.getMessage(), e);
                    // Continue - in-memory update was successful
                }
            }
        }
    }

    /**
     * Delete a resource from the Solid pod.
     */
    private void deleteFromPod(String resourceId) {
        if (solidPodClient != null && solidPodClient.isEnabled()) {
            AccessTokenContext context = AccessTokenContext.get();
            if (context != null) {
                try {
                    solidPodClient.deleteResource(resourceName, resourceId, context.getToken());
                } catch (Exception e) {
                    LOG.error("Failed to delete resource from Solid pod: {}", e.getMessage(), e);
                    // Continue - in-memory delete was successful
                }
            }
        }
    }

    /**
     * Get the SolidPodClient for loading resources.
     */
    protected SolidPodClient getSolidPodClient() {
        return solidPodClient;
    }

    /**
     * Get the resource name (type).
     */
    protected String getResourceName() {
        return resourceName;
    }
}
