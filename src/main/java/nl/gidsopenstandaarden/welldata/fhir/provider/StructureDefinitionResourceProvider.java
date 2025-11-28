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
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resource provider for StructureDefinition resources.
 * Serves FHIR profiles from the WellData Implementation Guide.
 */
@Component
public class StructureDefinitionResourceProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StructureDefinitionResourceProvider.class);

    private final Map<String, StructureDefinition> structureDefinitions = new ConcurrentHashMap<>();

    @Override
    public Class<StructureDefinition> getResourceType() {
        return StructureDefinition.class;
    }

    /**
     * Store a StructureDefinition in the provider.
     */
    public void store(StructureDefinition structureDefinition) {
        String id = structureDefinition.getIdElement().getIdPart();
        if (id == null || id.isEmpty()) {
            id = structureDefinition.getName();
        }
        structureDefinitions.put(id, structureDefinition);
        LOG.debug("Stored StructureDefinition: {} (url: {})", id, structureDefinition.getUrl());
    }

    @Read
    public StructureDefinition read(@IdParam IdType theId) {
        StructureDefinition sd = structureDefinitions.get(theId.getIdPart());
        if (sd == null) {
            throw new ResourceNotFoundException(theId);
        }
        return sd;
    }

    @Search
    public List<StructureDefinition> search(
            @OptionalParam(name = StructureDefinition.SP_URL) UriParam url,
            @OptionalParam(name = StructureDefinition.SP_NAME) StringParam name,
            @OptionalParam(name = StructureDefinition.SP_TYPE) TokenParam type,
            @OptionalParam(name = StructureDefinition.SP_STATUS) TokenParam status,
            @OptionalParam(name = StructureDefinition.SP_RES_ID) TokenParam id) {

        List<StructureDefinition> results = new ArrayList<>();

        for (StructureDefinition sd : structureDefinitions.values()) {
            boolean matches = true;

            if (url != null && !url.getValue().equals(sd.getUrl())) {
                matches = false;
            }
            if (name != null && (sd.getName() == null || !sd.getName().toLowerCase().contains(name.getValue().toLowerCase()))) {
                matches = false;
            }
            if (type != null && (sd.getType() == null || !sd.getType().equals(type.getValue()))) {
                matches = false;
            }
            if (status != null && (sd.getStatus() == null || !sd.getStatus().toCode().equals(status.getValue()))) {
                matches = false;
            }
            if (id != null && !sd.getIdElement().getIdPart().equals(id.getValue())) {
                matches = false;
            }

            if (matches) {
                results.add(sd);
            }
        }

        return results;
    }

    /**
     * Get the number of stored StructureDefinitions.
     */
    public int size() {
        return structureDefinitions.size();
    }
}
