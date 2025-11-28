package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ImplementationGuide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resource provider for ImplementationGuide resources.
 * Serves the WellData Implementation Guide metadata.
 */
@Component
public class ImplementationGuideResourceProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ImplementationGuideResourceProvider.class);

    private final Map<String, ImplementationGuide> implementationGuides = new ConcurrentHashMap<>();

    @Override
    public Class<ImplementationGuide> getResourceType() {
        return ImplementationGuide.class;
    }

    /**
     * Store an ImplementationGuide in the provider.
     */
    public void store(ImplementationGuide ig) {
        String id = ig.getIdElement().getIdPart();
        if (id == null || id.isEmpty()) {
            id = ig.getName();
        }
        implementationGuides.put(id, ig);
        LOG.debug("Stored ImplementationGuide: {} (url: {})", id, ig.getUrl());
    }

    @Read
    public ImplementationGuide read(@IdParam IdType theId) {
        ImplementationGuide ig = implementationGuides.get(theId.getIdPart());
        if (ig == null) {
            throw new ResourceNotFoundException(theId);
        }
        return ig;
    }

    @Search
    public List<ImplementationGuide> search(
            @OptionalParam(name = ImplementationGuide.SP_URL) UriParam url,
            @OptionalParam(name = ImplementationGuide.SP_NAME) StringParam name,
            @OptionalParam(name = ImplementationGuide.SP_STATUS) TokenParam status,
            @OptionalParam(name = ImplementationGuide.SP_RES_ID) TokenParam id) {

        List<ImplementationGuide> results = new ArrayList<>();

        for (ImplementationGuide ig : implementationGuides.values()) {
            boolean matches = true;

            if (url != null && !url.getValue().equals(ig.getUrl())) {
                matches = false;
            }
            if (name != null && (ig.getName() == null || !ig.getName().toLowerCase().contains(name.getValue().toLowerCase()))) {
                matches = false;
            }
            if (status != null && (ig.getStatus() == null || !ig.getStatus().toCode().equals(status.getValue()))) {
                matches = false;
            }
            if (id != null && !ig.getIdElement().getIdPart().equals(id.getValue())) {
                matches = false;
            }

            if (matches) {
                results.add(ig);
            }
        }

        return results;
    }

    /**
     * Get the number of stored ImplementationGuides.
     */
    public int size() {
        return implementationGuides.size();
    }
}
