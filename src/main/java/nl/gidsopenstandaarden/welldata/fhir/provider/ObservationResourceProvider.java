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
import nl.gidsopenstandaarden.welldata.fhir.service.SolidPodClient;
import org.hl7.fhir.r4.model.Observation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resource provider for Observation resources with WellData-specific search parameters.
 */
public class ObservationResourceProvider extends WellDataResourceProvider<Observation> {

    public ObservationResourceProvider(FhirContext fhirContext, SessionManager sessionManager, SolidPodClient solidPodClient) {
        super(fhirContext, Observation.class, sessionManager, solidPodClient);
    }

    @Search
    public IBundleProvider searchObservations(
            @OptionalParam(name = Observation.SP_SUBJECT) ReferenceParam subject,
            @OptionalParam(name = Observation.SP_CODE) TokenParam code,
            @OptionalParam(name = Observation.SP_DATE) DateRangeParam date,
            @OptionalParam(name = Observation.SP_STATUS) TokenParam status,
            @OptionalParam(name = Observation.SP_CATEGORY) TokenParam category,
            RequestDetails requestDetails) {

        List<Observation> results = getAllResources().stream()
                .filter(obs -> matchesSubject(obs, subject))
                .filter(obs -> matchesCode(obs, code))
                .filter(obs -> matchesDate(obs, date))
                .filter(obs -> matchesStatus(obs, status))
                .filter(obs -> matchesCategory(obs, category))
                .collect(Collectors.toList());

        return new SimpleBundleProvider(results);
    }

    private boolean matchesSubject(Observation obs, ReferenceParam subject) {
        if (subject == null) return true;
        if (obs.getSubject() == null || obs.getSubject().getReference() == null) return false;

        String ref = obs.getSubject().getReference();
        String searchValue = subject.getValue();

        // Handle both "Patient/123" and just "123" formats
        return ref.equals(searchValue) ||
               ref.endsWith("/" + searchValue) ||
               ref.equals("Patient/" + searchValue);
    }

    private boolean matchesCode(Observation obs, TokenParam code) {
        if (code == null) return true;
        if (obs.getCode() == null || obs.getCode().getCoding() == null) return false;

        return obs.getCode().getCoding().stream()
                .anyMatch(c -> {
                    if (code.getSystem() != null && !code.getSystem().equals(c.getSystem())) {
                        return false;
                    }
                    return code.getValue().equals(c.getCode());
                });
    }

    private boolean matchesDate(Observation obs, DateRangeParam date) {
        if (date == null) return true;
        if (obs.getEffective() == null) return false;

        // Handle effectiveDateTime
        if (obs.hasEffectiveDateTimeType()) {
            java.util.Date effectiveDate = obs.getEffectiveDateTimeType().getValue();
            if (effectiveDate == null) return false;

            if (date.getLowerBound() != null && effectiveDate.before(date.getLowerBound().getValue())) {
                return false;
            }
            if (date.getUpperBound() != null && effectiveDate.after(date.getUpperBound().getValue())) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesStatus(Observation obs, TokenParam status) {
        if (status == null) return true;
        if (obs.getStatus() == null) return false;

        return status.getValue().equalsIgnoreCase(obs.getStatus().toCode());
    }

    private boolean matchesCategory(Observation obs, TokenParam category) {
        if (category == null) return true;
        if (obs.getCategory() == null || obs.getCategory().isEmpty()) return false;

        return obs.getCategory().stream()
                .filter(cat -> cat.getCoding() != null)
                .flatMap(cat -> cat.getCoding().stream())
                .anyMatch(c -> {
                    if (category.getSystem() != null && !category.getSystem().equals(c.getSystem())) {
                        return false;
                    }
                    return category.getValue().equals(c.getCode());
                });
    }
}
