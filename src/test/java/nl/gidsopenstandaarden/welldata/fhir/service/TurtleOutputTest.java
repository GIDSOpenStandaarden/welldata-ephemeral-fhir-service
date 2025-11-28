package nl.gidsopenstandaarden.welldata.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;

/**
 * Test to verify the Turtle output from HAPI FHIR RDF encoder.
 */
public class TurtleOutputTest {

    @Test
    public void testTurtleOutput() {
        FhirContext ctx = FhirContext.forR4();

        // Create a simple observation
        Observation obs = new Observation();
        obs.setId("test-1");
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.setCode(new CodeableConcept().addCoding(
            new Coding()
                .setSystem("http://snomed.info/sct")
                .setCode("27113001")
                .setDisplay("Body Weight")
        ));
        obs.setSubject(new Reference("Patient/test"));
        obs.setValue(new Quantity()
            .setValue(75)
            .setUnit("kg")
            .setSystem("http://unitsofmeasure.org")
            .setCode("kg"));

        // Convert to Turtle
        IParser rdfParser = ctx.newRDFParser();
        String turtle = rdfParser.encodeResourceToString(obs);

        System.out.println("=== Generated Turtle ===");
        String[] lines = turtle.split("\n");
        for (int i = 0; i < lines.length; i++) {
            System.out.printf("%3d: %s%n", i + 1, lines[i]);
        }
        System.out.println("=== End Turtle ===");
        System.out.println("Line 9: " + (lines.length >= 9 ? lines[8] : "N/A"));

        // Verify it's not empty and contains expected content
        assert turtle != null && !turtle.isEmpty();
        assert turtle.contains("fhir:Observation");
    }
}
