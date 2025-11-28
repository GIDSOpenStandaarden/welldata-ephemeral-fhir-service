package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import org.hl7.fhir.r4.model.Questionnaire;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resource provider for Questionnaire resources.
 */
public class QuestionnaireResourceProvider extends WellDataResourceProvider<Questionnaire> {

    public QuestionnaireResourceProvider(FhirContext fhirContext, SessionManager sessionManager) {
        super(fhirContext, Questionnaire.class, sessionManager);
    }

    @Search
    public IBundleProvider searchQuestionnaires(
            @OptionalParam(name = Questionnaire.SP_IDENTIFIER) TokenParam identifier,
            @OptionalParam(name = Questionnaire.SP_NAME) StringParam name,
            @OptionalParam(name = Questionnaire.SP_TITLE) StringParam title,
            @OptionalParam(name = Questionnaire.SP_STATUS) TokenParam status,
            RequestDetails requestDetails) {

        List<Questionnaire> results = getAllResources().stream()
                .filter(q -> matchesIdentifier(q, identifier))
                .filter(q -> matchesName(q, name))
                .filter(q -> matchesTitle(q, title))
                .filter(q -> matchesStatus(q, status))
                .collect(Collectors.toList());

        return new SimpleBundleProvider(results);
    }

    private boolean matchesIdentifier(Questionnaire questionnaire, TokenParam identifier) {
        if (identifier == null) return true;
        if (questionnaire.getIdentifier() == null || questionnaire.getIdentifier().isEmpty()) return false;

        return questionnaire.getIdentifier().stream()
                .anyMatch(id -> {
                    if (identifier.getSystem() != null && !identifier.getSystem().equals(id.getSystem())) {
                        return false;
                    }
                    return identifier.getValue().equals(id.getValue());
                });
    }

    private boolean matchesName(Questionnaire questionnaire, StringParam name) {
        if (name == null) return true;
        if (questionnaire.getName() == null) return false;

        return questionnaire.getName().toLowerCase().contains(name.getValue().toLowerCase());
    }

    private boolean matchesTitle(Questionnaire questionnaire, StringParam title) {
        if (title == null) return true;
        if (questionnaire.getTitle() == null) return false;

        return questionnaire.getTitle().toLowerCase().contains(title.getValue().toLowerCase());
    }

    private boolean matchesStatus(Questionnaire questionnaire, TokenParam status) {
        if (status == null) return true;
        if (questionnaire.getStatus() == null) return false;

        return status.getValue().equalsIgnoreCase(questionnaire.getStatus().toCode());
    }
}
