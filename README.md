# WellData Ephemeral FHIR Service

An ephemeral FHIR R4 service for the WellData project, providing session-scoped in-memory FHIR resource storage with HAPI FHIR.

## Overview

The WellData Ephemeral FHIR Service implements the [ephemeral FHIR service concept](https://gidsopenstandaarden.github.io/welldata-implementation-guide/technical-ephemeral-fhir-service.html) from the WellData Implementation Guide. It provides:

- **Session-scoped storage**: Each access token gets its own isolated FHIR resource store
- **Token-bound lifecycle**: Sessions are tied to JWT access token expiry
- **In-memory operation**: Fast, ephemeral storage for client-side FHIR operations
- **WellData profiles**: Serves StructureDefinitions from the WellData Implementation Guide

### Current Implementation Status

| Feature | Status |
|---------|--------|
| In-memory FHIR R4 storage | Implemented |
| Session isolation per access token | Implemented |
| JWT token parsing (jti, sub, exp) | Implemented |
| Automatic session cleanup | Implemented |
| WellData IG profile serving | Implemented |
| Solid pod integration | Not yet implemented |
| Token signature validation | Not yet implemented |

## Quick Start

### Using Docker Compose (Recommended)

Start all services (FHIR server, Solid pod, and demo client):

```bash
docker compose up --build
```

For development with auto-rebuild on file changes:

```bash
docker compose watch
```

Services will be available at:
- **Demo client**: http://localhost:3001
- **FHIR server**: http://localhost:8080/fhir
- **Solid pod**: http://localhost:3000

### Using Maven (FHIR server only)

```bash
./mvnw spring-boot:run
```

The FHIR server will be available at http://localhost:8080/fhir

## Demo Client

The demo client is a React application that demonstrates the WellData workflow:

1. **Login with Solid** - Authenticate using Solid OIDC (default: local Solid pod)
2. **Fill out a health questionnaire** - Answer questions about vitals, wellbeing, lifestyle
3. **Submit responses** - Creates a QuestionnaireResponse and linked Observations
4. **View history** - See submitted responses and observations

### Using an External Solid Provider

The demo client supports connecting to external Solid providers instead of the local pod:

1. Click "Use different Solid provider" on the login screen
2. Enter the URL of your Solid provider (e.g., `https://solidcommunity.net`, `https://login.inrupt.com`)
3. Click "Login with Solid"

This allows you to use your existing Solid identity with social login providers (Google, etc.) if your Solid provider supports it.

The demo client uses [Solid-OIDC](https://solid.github.io/solid-oidc/) with a dynamically generated Client ID Document served at `/clientid.jsonld`. This document is automatically configured based on the deployment URL, making it work on any host without configuration changes.

## Authentication

The service requires a JWT Bearer token for accessing patient data endpoints. The token is used to scope data to a specific session.

### Public Endpoints (no authentication required)

- `GET /fhir/metadata` - CapabilityStatement
- `GET /fhir/StructureDefinition` - WellData profiles
- `GET /fhir/ImplementationGuide` - Implementation guide metadata

### Protected Endpoints (Bearer token required)

- `/fhir/Patient`
- `/fhir/Observation`
- `/fhir/Questionnaire`
- `/fhir/QuestionnaireResponse`

### Example with Authentication

```bash
# Get a JWT token from your identity provider, then:
curl -H "Authorization: Bearer <your-jwt-token>" http://localhost:8080/fhir/Patient
```

The service extracts the following claims from the JWT:
- `jti` - Used as session identifier (falls back to token hash if not present)
- `sub` - Subject/user identifier (logged for debugging)
- `exp` - Expiry time (session is cleaned up after this time)

## API Examples

### Get CapabilityStatement

```bash
curl http://localhost:8080/fhir/metadata
```

### Get WellData Profiles

```bash
curl http://localhost:8080/fhir/StructureDefinition
```

### Search Patients (requires auth)

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/fhir/Patient
```

### Search Observations by Patient (requires auth)

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/fhir/Observation?subject=Patient/example-welldata-patient"
```

### Create a new Patient (requires auth)

```bash
curl -X POST http://localhost:8080/fhir/Patient \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType": "Patient", "name": [{"family": "Test", "given": ["User"]}]}'
```

## Supported Resources

| Resource | Search Parameters |
|----------|-------------------|
| Patient | identifier, name, family, given, birthdate |
| Observation | subject, code, date, status, category |
| Questionnaire | identifier, name, title, status |
| QuestionnaireResponse | subject, questionnaire, status, authored, author |
| StructureDefinition | url, name, type, status |
| ImplementationGuide | url, name, status |

## Architecture

```
┌─────────────────┐     ┌─────────────────────────────────────────┐
│   Client App    │────▶│      Ephemeral FHIR Service             │
│                 │◀────│                                         │
└─────────────────┘     │  ┌─────────────────────────────────┐    │
                        │  │  AccessTokenInterceptor         │    │
       JWT Token ──────▶│  │  - Extract Bearer token         │    │
                        │  │  - Decode JWT (jti, sub, exp)   │    │
                        │  │  - Create/retrieve session      │    │
                        │  └─────────────────────────────────┘    │
                        │                  │                      │
                        │                  ▼                      │
                        │  ┌─────────────────────────────────┐    │
                        │  │  SessionManager                 │    │
                        │  │  - Per-token resource stores    │    │
                        │  │  - Automatic cleanup on expiry  │    │
                        │  └─────────────────────────────────┘    │
                        │                  │                      │
                        │                  ▼                      │
                        │  ┌─────────────────────────────────┐    │
                        │  │  WellDataResourceProvider       │    │
                        │  │  - Session-scoped CRUD          │    │
                        │  │  - In-memory storage            │    │
                        │  └─────────────────────────────────┘    │
                        └─────────────────────────────────────────┘
```

## Configuration

Configuration is managed via `src/main/resources/application.yaml`:

```yaml
server:
  port: 8080

welldata:
  testdata:
    path: classpath:testdata
  ig:
    url: https://github.com/GIDSOpenStandaarden/welldata-implementation-guide/releases/download/v0.1.1/welldata-0.1.1.tgz
```

## Development

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (optional)

### Build

```bash
./mvnw clean package
```

### Run Tests

```bash
./mvnw test
```

### Project Structure

```
src/main/java/nl/gidsopenstandaarden/welldata/fhir/
├── Application.java                     # Spring Boot entry point
├── config/
│   └── WellDataRestfulServer.java      # HAPI FHIR server configuration
├── context/
│   └── AccessTokenContext.java         # ThreadLocal token context
├── interceptor/
│   └── AccessTokenInterceptor.java     # JWT extraction and session setup
├── provider/
│   ├── WellDataResourceProvider.java   # Base session-scoped provider
│   ├── PatientResourceProvider.java
│   ├── ObservationResourceProvider.java
│   ├── QuestionnaireResourceProvider.java
│   ├── QuestionnaireResponseResourceProvider.java
│   ├── StructureDefinitionResourceProvider.java
│   └── ImplementationGuideResourceProvider.java
└── service/
    ├── JsonDataLoader.java             # Test data loading
    ├── IgPackageLoader.java            # IG package loading
    └── SessionManager.java             # Session lifecycle management
```

## Related Projects

- [WellData Implementation Guide](https://gidsopenstandaarden.github.io/welldata-implementation-guide/) - FHIR profiles and specifications
- [HAPI FHIR](https://hapifhir.io/) - FHIR implementation library

## License

See LICENSE file for details.
