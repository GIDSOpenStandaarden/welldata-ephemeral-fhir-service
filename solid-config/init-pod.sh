#!/bin/bash
# Initialize the Solid pod with WellData FHIR resource containers
# This script creates the directory structure for FHIR resources

POD_URL="${SOLID_POD_URL:-http://localhost:3000}"

echo "Initializing WellData pod structure at ${POD_URL}..."

# Wait for pod to be ready
until curl -s "${POD_URL}/.well-known/solid" > /dev/null 2>&1; do
    echo "Waiting for Solid pod to be ready..."
    sleep 2
done

echo "Solid pod is ready. Creating FHIR containers..."

# Create the weare/fhir container hierarchy
# Using LDP container creation with proper headers

# Create root container: /weare/
curl -X PUT "${POD_URL}/weare/" \
    -H "Content-Type: text/turtle" \
    -H "Link: <http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"" \
    -d "@prefix ldp: <http://www.w3.org/ns/ldp#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
<> a ldp:BasicContainer ;
   dcterms:title \"WellData Health Data\" ."

# Create FHIR container: /weare/fhir/
curl -X PUT "${POD_URL}/weare/fhir/" \
    -H "Content-Type: text/turtle" \
    -H "Link: <http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"" \
    -d "@prefix ldp: <http://www.w3.org/ns/ldp#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
<> a ldp:BasicContainer ;
   dcterms:title \"FHIR Resources\" ."

# Create resource-type containers
for resource_type in Patient Observation Questionnaire QuestionnaireResponse; do
    echo "Creating container for ${resource_type}..."
    curl -X PUT "${POD_URL}/weare/fhir/${resource_type}/" \
        -H "Content-Type: text/turtle" \
        -H "Link: <http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"" \
        -d "@prefix ldp: <http://www.w3.org/ns/ldp#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
<> a ldp:BasicContainer ;
   dcterms:title \"${resource_type} Resources\" ."
done

echo "WellData pod structure initialized successfully!"
echo ""
echo "Container structure:"
echo "  ${POD_URL}/weare/"
echo "  ${POD_URL}/weare/fhir/"
echo "  ${POD_URL}/weare/fhir/Patient/"
echo "  ${POD_URL}/weare/fhir/Observation/"
echo "  ${POD_URL}/weare/fhir/Questionnaire/"
echo "  ${POD_URL}/weare/fhir/QuestionnaireResponse/"
