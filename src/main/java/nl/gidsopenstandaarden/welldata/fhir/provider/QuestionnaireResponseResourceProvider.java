package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import org.hl7.fhir.r4.model.QuestionnaireResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resource provider for QuestionnaireResponse resources with WellData-specific search parameters.
 */
public class QuestionnaireResponseResourceProvider extends WellDataResourceProvider<QuestionnaireResponse> {

    public QuestionnaireResponseResourceProvider(FhirContext fhirContext, SessionManager sessionManager) {
        super(fhirContext, QuestionnaireResponse.class, sessionManager);
    }

    @Search
    public IBundleProvider searchQuestionnaireResponses(
            @OptionalParam(name = QuestionnaireResponse.SP_SUBJECT) ReferenceParam subject,
            @OptionalParam(name = QuestionnaireResponse.SP_QUESTIONNAIRE) ReferenceParam questionnaire,
            @OptionalParam(name = QuestionnaireResponse.SP_STATUS) TokenParam status,
            @OptionalParam(name = QuestionnaireResponse.SP_AUTHORED) DateRangeParam authored,
            @OptionalParam(name = QuestionnaireResponse.SP_AUTHOR) ReferenceParam author,
            RequestDetails requestDetails) {

        List<QuestionnaireResponse> results = getAllResources().stream()
                .filter(qr -> matchesSubject(qr, subject))
                .filter(qr -> matchesQuestionnaire(qr, questionnaire))
                .filter(qr -> matchesStatus(qr, status))
                .filter(qr -> matchesAuthored(qr, authored))
                .filter(qr -> matchesAuthor(qr, author))
                .collect(Collectors.toList());

        return new SimpleBundleProvider(results);
    }

    private boolean matchesSubject(QuestionnaireResponse qr, ReferenceParam subject) {
        if (subject == null) return true;
        if (qr.getSubject() == null || qr.getSubject().getReference() == null) return false;

        String ref = qr.getSubject().getReference();
        String searchValue = subject.getValue();

        return ref.equals(searchValue) ||
               ref.endsWith("/" + searchValue) ||
               ref.equals("Patient/" + searchValue);
    }

    private boolean matchesQuestionnaire(QuestionnaireResponse qr, ReferenceParam questionnaire) {
        if (questionnaire == null) return true;
        if (qr.getQuestionnaire() == null) return false;

        String ref = qr.getQuestionnaire();
        String searchValue = questionnaire.getValue();

        return ref.equals(searchValue) ||
               ref.endsWith("/" + searchValue) ||
               ref.contains(searchValue);
    }

    private boolean matchesStatus(QuestionnaireResponse qr, TokenParam status) {
        if (status == null) return true;
        if (qr.getStatus() == null) return false;

        return status.getValue().equalsIgnoreCase(qr.getStatus().toCode());
    }

    private boolean matchesAuthored(QuestionnaireResponse qr, DateRangeParam authored) {
        if (authored == null) return true;
        if (qr.getAuthored() == null) return false;

        java.util.Date authoredDate = qr.getAuthored();

        if (authored.getLowerBound() != null && authoredDate.before(authored.getLowerBound().getValue())) {
            return false;
        }
        if (authored.getUpperBound() != null && authoredDate.after(authored.getUpperBound().getValue())) {
            return false;
        }

        return true;
    }

    private boolean matchesAuthor(QuestionnaireResponse qr, ReferenceParam author) {
        if (author == null) return true;
        if (qr.getAuthor() == null || qr.getAuthor().getReference() == null) return false;

        String ref = qr.getAuthor().getReference();
        String searchValue = author.getValue();

        return ref.equals(searchValue) ||
               ref.endsWith("/" + searchValue);
    }
}
