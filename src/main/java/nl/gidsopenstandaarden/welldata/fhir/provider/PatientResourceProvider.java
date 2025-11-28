package nl.gidsopenstandaarden.welldata.fhir.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import nl.gidsopenstandaarden.welldata.fhir.service.SessionManager;
import org.hl7.fhir.r4.model.Patient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resource provider for Patient resources with WellData-specific search parameters.
 */
public class PatientResourceProvider extends WellDataResourceProvider<Patient> {

    public PatientResourceProvider(FhirContext fhirContext, SessionManager sessionManager) {
        super(fhirContext, Patient.class, sessionManager);
    }

    @Search
    public IBundleProvider searchPatients(
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam identifier,
            @OptionalParam(name = Patient.SP_NAME) StringParam name,
            @OptionalParam(name = Patient.SP_FAMILY) StringParam family,
            @OptionalParam(name = Patient.SP_GIVEN) StringParam given,
            @OptionalParam(name = Patient.SP_BIRTHDATE) DateParam birthdate,
            RequestDetails requestDetails) {

        List<Patient> results = getAllResources().stream()
                .filter(p -> matchesIdentifier(p, identifier))
                .filter(p -> matchesName(p, name))
                .filter(p -> matchesFamily(p, family))
                .filter(p -> matchesGiven(p, given))
                .filter(p -> matchesBirthdate(p, birthdate))
                .collect(Collectors.toList());

        return new SimpleBundleProvider(results);
    }

    private boolean matchesIdentifier(Patient patient, TokenParam identifier) {
        if (identifier == null) return true;
        if (patient.getIdentifier() == null || patient.getIdentifier().isEmpty()) return false;

        return patient.getIdentifier().stream()
                .anyMatch(id -> {
                    if (identifier.getSystem() != null && !identifier.getSystem().equals(id.getSystem())) {
                        return false;
                    }
                    return identifier.getValue().equals(id.getValue());
                });
    }

    private boolean matchesName(Patient patient, StringParam name) {
        if (name == null) return true;
        if (patient.getName() == null || patient.getName().isEmpty()) return false;

        String searchValue = name.getValue().toLowerCase();
        return patient.getName().stream()
                .anyMatch(n -> {
                    String fullName = "";
                    if (n.hasFamily()) fullName += n.getFamily().toLowerCase() + " ";
                    if (n.hasGiven()) fullName += n.getGiven().stream()
                            .map(g -> g.getValue().toLowerCase())
                            .collect(Collectors.joining(" "));
                    return fullName.contains(searchValue);
                });
    }

    private boolean matchesFamily(Patient patient, StringParam family) {
        if (family == null) return true;
        if (patient.getName() == null || patient.getName().isEmpty()) return false;

        String searchValue = family.getValue().toLowerCase();
        return patient.getName().stream()
                .filter(n -> n.hasFamily())
                .anyMatch(n -> n.getFamily().toLowerCase().contains(searchValue));
    }

    private boolean matchesGiven(Patient patient, StringParam given) {
        if (given == null) return true;
        if (patient.getName() == null || patient.getName().isEmpty()) return false;

        String searchValue = given.getValue().toLowerCase();
        return patient.getName().stream()
                .filter(n -> n.hasGiven())
                .flatMap(n -> n.getGiven().stream())
                .anyMatch(g -> g.getValue().toLowerCase().contains(searchValue));
    }

    private boolean matchesBirthdate(Patient patient, DateParam birthdate) {
        if (birthdate == null) return true;
        if (patient.getBirthDate() == null) return false;

        // Simple date comparison - could be enhanced with prefix handling
        return patient.getBirthDate().equals(birthdate.getValue());
    }
}
