# WellData Ephemeral FHIR Service

## Project Overview

This is an ephemeral FHIR R4 service for the WellData project. It provides in-memory FHIR resource storage with plans for future Solid pod integration.

**Specification**: https://gidsopenstandaarden.github.io/welldata-implementation-guide/technical-ephemeral-fhir-service.html

## Related Projects

| Project | Description | Location |
|---------|-------------|----------|
| **WellData Implementation Guide** | FHIR profiles, documentation, specifications | `../gidsopenstandaarden-welldata-ig` |
| **WellData IG (published)** | Online documentation | https://gidsopenstandaarden.github.io/welldata-implementation-guide/ |

### Key Specification Pages

- [Ephemeral FHIR Service](https://gidsopenstandaarden.github.io/welldata-implementation-guide/technical-ephemeral-fhir-service.html) - Architecture and design
- [Dynamic Data](https://gidsopenstandaarden.github.io/welldata-implementation-guide/dynamic-data.html) - Shared dynamic data model
- [Pod Access](https://gidsopenstandaarden.github.io/welldata-implementation-guide/pod-access.html) - Solid pod access patterns
- [User Authentication](https://gidsopenstandaarden.github.io/welldata-implementation-guide/technical-user-authentication.html) - Authentication flows

## Architecture

### Ephemeral FHIR Service Concept

The ephemeral FHIR service solves the impedance mismatch between FHIR (an exchange format) and Solid pods (canonical RDF storage):

1. **Token-bound Lifecycle**: Service instances are tied to access token expiry (`exp` claim)
2. **In-memory Storage**: Resources loaded from pod at session start
3. **Immediate Persistence**: Changes written back to pod immediately
4. **Full FHIR API**: Complete search, CRUD, history support
5. **Session Isolation**: Each user/token gets their own instance

```
┌─────────────┐     ┌──────────────────┐     ┌────────────┐
│  Client App │────▶│ Ephemeral FHIR   │────▶│ Solid Pod  │
│             │◀────│ Service          │◀────│ (RDF/TTL)  │
└─────────────┘     └──────────────────┘     └────────────┘
                           │
                    In-memory FHIR
                    resource store
```

### Core Components

- **WellDataResourceProvider**: Base in-memory resource provider (inspired by HAPI FHIR's HashMapResourceProvider)
- **Specialized Providers**: Patient, Observation, Questionnaire, QuestionnaireResponse providers with search capabilities
- **WellDataRestfulServer**: HAPI FHIR RestfulServer configuration
- **TtlDataLoader**: Service for loading FHIR resources from Turtle (TTL/RDF) files

### Key Technologies

- **HAPI FHIR 7.4.0**: FHIR R4 implementation
- **Spring Boot 3.3.5**: Application framework
- **Apache Jena 5.1.0**: RDF/TTL parsing (for Solid pod integration)

## Project Structure

```
src/main/java/nl/gidsopenstandaarden/welldata/fhir/
├── Application.java                    # Spring Boot entry point
├── config/
│   └── WellDataRestfulServer.java     # FHIR server configuration
├── provider/
│   ├── WellDataResourceProvider.java  # Base in-memory provider
│   ├── PatientResourceProvider.java
│   ├── ObservationResourceProvider.java
│   ├── QuestionnaireResourceProvider.java
│   └── QuestionnaireResponseResourceProvider.java
└── service/
    └── TtlDataLoader.java             # TTL file loader

src/main/resources/
├── application.yaml                    # Configuration
└── testdata/                          # Sample TTL resources
    ├── Patient/
    ├── Observation/
    ├── Questionnaire/
    └── QuestionnaireResponse/
```

## Building and Running

### Local Development

```bash
./mvnw spring-boot:run
```

### Docker

```bash
docker-compose up --build
```

### Access Points

- FHIR Base URL: http://localhost:8080/fhir
- CapabilityStatement: http://localhost:8080/fhir/metadata
- Patient search: http://localhost:8080/fhir/Patient
- Observation search: http://localhost:8080/fhir/Observation

## API Endpoints

The server supports standard FHIR R4 operations:

### CRUD Operations
- `GET /fhir/{ResourceType}/{id}` - Read resource
- `POST /fhir/{ResourceType}` - Create resource
- `PUT /fhir/{ResourceType}/{id}` - Update resource
- `DELETE /fhir/{ResourceType}/{id}` - Delete resource

### Search Parameters

#### Patient
- `identifier` - Patient identifier
- `name` - Patient name (partial match)
- `family` - Family name
- `given` - Given name
- `birthdate` - Birth date

#### Observation
- `subject` - Patient reference
- `code` - Observation code (SNOMED CT)
- `date` - Effective date range
- `status` - Observation status
- `category` - Observation category

#### Questionnaire
- `identifier` - Questionnaire identifier
- `name` - Questionnaire name
- `title` - Questionnaire title
- `status` - Publication status

#### QuestionnaireResponse
- `subject` - Patient reference
- `questionnaire` - Questionnaire reference
- `status` - Response status
- `authored` - Authored date range
- `author` - Author reference

## WellData Observation Types

The service supports the following WellData observation types (SNOMED CT codes):

| Observation         | SNOMED Code | Category       |
|---------------------|-------------|----------------|
| Body Weight         | 27113001    | vital-signs    |
| Body Height         | 50373000    | vital-signs    |
| BMI                 | 60621009    | vital-signs    |
| Waist Circumference | 276361009   | vital-signs    |
| Systolic BP         | 271649006   | vital-signs    |
| Total Cholesterol   | 121868005   | laboratory     |
| HDL Cholesterol     | 28036006    | laboratory     |
| Cholesterol Ratio   | 313811003   | laboratory     |
| Well/Mood           | 373931001   | survey         |
| Physical Limitation | 301667007   | survey         |
| Stress              | 262188008   | survey         |
| Daily Life          | 118227000   | survey         |
| Social Contact      | 440379008   | social-history |
| Physical Exercise   | 256235009   | activity       |
| Smoking Status      | 365981007   | social-history |
| Cigarettes/Day      | 266918002   | social-history |
| Alcohol Status      | 228273003   | social-history |
| Alcohol Frequency   | 228308007   | social-history |
| Alcohol Consumption | 160573003   | social-history |

## Code Conventions

- Use HAPI FHIR annotations for REST operations (@Read, @Create, @Update, @Delete, @Search)
- Resource providers extend `WellDataResourceProvider<T>`
- Use `FhirContext.forR4()` for FHIR R4 operations
- Store resources in memory using ConcurrentHashMap for thread safety
- TTL files use FHIR RDF format (generated by IG Publisher)

## Git Conventions

- Use `git rm` instead of plain `rm` when deleting files
- Use `git mv` instead of plain `mv` when moving files
- Always add new files to git
- Do not add generated files (target/, *.jar) to git

## Future Development

### Phase 1: Solid Pod Integration
- Implement pod client using Solid OIDC
- Load resources from pod at service initialization
- Write changes back to pod on create/update/delete
- Handle pod authentication via access tokens

### Phase 2: Token-bound Lifecycle
- Parse JWT access tokens for `exp` claim
- Use `jti` or token hash as instance identifier
- Automatic cleanup on token expiry
- New instance on token refresh

### Phase 3: Multi-tenant Support
- Separate resource stores per user/session
- Instance routing based on token
- Shared questionnaire definitions
- Per-user observation data

## Testing

### Manual Testing with curl

```bash
# Get capability statement
curl http://localhost:8080/fhir/metadata

# Search all patients
curl http://localhost:8080/fhir/Patient

# Search observations by patient
curl "http://localhost:8080/fhir/Observation?subject=Patient/example-welldata-patient"

# Search observations by code
curl "http://localhost:8080/fhir/Observation?code=60621009"

# Create a new observation
curl -X POST http://localhost:8080/fhir/Observation \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Observation","status":"final","code":{"coding":[{"system":"http://snomed.info/sct","code":"27113001"}]},"subject":{"reference":"Patient/example-welldata-patient"},"valueQuantity":{"value":75,"unit":"kg"}}'
```

## Reference Implementation

This project was inspired by:
- HAPI FHIR HashMapResourceProvider pattern
- Koppeltaal 2.0 FHIR HAPI Server (`/Users/roland/Documents/Projects/HeadEase/Koppeltaal/Koppeltaal-2.0-FHIR-HAPI-Server`)

The test data (TTL files) comes from the WellData IG project output.
- On each commit, update the @README.md and @CHANGELOG.md