package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static resource provider for Questionnaire resources.
 *
 * Questionnaires are shared definitions (not user data) and are loaded from
 * local files at startup. They do not require authentication to access.
 *
 * TODO: Future enhancements:
 * - Support proxying Questionnaires from a remote FHIR server
 * - Allow configuration of Questionnaire sources (local files, remote server, or both)
 * - Support caching with TTL for remote Questionnaires
 */
@Component
public class StaticQuestionnaireResourceProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StaticQuestionnaireResourceProvider.class);

    private final Map<String, Questionnaire> questionnaires = new ConcurrentHashMap<>();

    @Override
    public Class<Questionnaire> getResourceType() {
        return Questionnaire.class;
    }

    /**
     * Store a Questionnaire in the provider.
     */
    public void store(Questionnaire questionnaire) {
        String id = questionnaire.getIdElement().getIdPart();
        if (id == null || id.isEmpty()) {
            id = questionnaire.getName();
        }
        if (id == null || id.isEmpty()) {
            LOG.warn("Questionnaire has no ID or name, skipping");
            return;
        }
        questionnaires.put(id, questionnaire);
        LOG.debug("Stored Questionnaire: {} (url: {}, title: {})", id, questionnaire.getUrl(), questionnaire.getTitle());
    }

    @Read
    public Questionnaire read(@IdParam IdType theId) {
        Questionnaire q = questionnaires.get(theId.getIdPart());
        if (q == null) {
            throw new ResourceNotFoundException(theId);
        }
        return q;
    }

    @Search
    public List<Questionnaire> search(
            @OptionalParam(name = Questionnaire.SP_URL) UriParam url,
            @OptionalParam(name = Questionnaire.SP_IDENTIFIER) TokenParam identifier,
            @OptionalParam(name = Questionnaire.SP_NAME) StringParam name,
            @OptionalParam(name = Questionnaire.SP_TITLE) StringParam title,
            @OptionalParam(name = Questionnaire.SP_STATUS) TokenParam status,
            @OptionalParam(name = Questionnaire.SP_RES_ID) TokenParam id) {

        List<Questionnaire> results = new ArrayList<>();

        for (Questionnaire q : questionnaires.values()) {
            boolean matches = true;

            if (url != null && !url.getValue().equals(q.getUrl())) {
                matches = false;
            }
            if (identifier != null) {
                matches = matchesIdentifier(q, identifier);
            }
            if (name != null && (q.getName() == null || !q.getName().toLowerCase().contains(name.getValue().toLowerCase()))) {
                matches = false;
            }
            if (title != null && (q.getTitle() == null || !q.getTitle().toLowerCase().contains(title.getValue().toLowerCase()))) {
                matches = false;
            }
            if (status != null && (q.getStatus() == null || !q.getStatus().toCode().equals(status.getValue()))) {
                matches = false;
            }
            if (id != null && !q.getIdElement().getIdPart().equals(id.getValue())) {
                matches = false;
            }

            if (matches) {
                results.add(q);
            }
        }

        return results;
    }

    private boolean matchesIdentifier(Questionnaire questionnaire, TokenParam identifier) {
        if (questionnaire.getIdentifier() == null || questionnaire.getIdentifier().isEmpty()) {
            return false;
        }
        return questionnaire.getIdentifier().stream()
                .anyMatch(id -> {
                    if (identifier.getSystem() != null && !identifier.getSystem().equals(id.getSystem())) {
                        return false;
                    }
                    return identifier.getValue().equals(id.getValue());
                });
    }

    /**
     * Get the number of stored Questionnaires.
     */
    public int size() {
        return questionnaires.size();
    }
}
