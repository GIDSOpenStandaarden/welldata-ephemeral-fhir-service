# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- GitHub Actions workflow for CI/CD (`.github/workflows/build-deploy.yml`)
  - Builds and tests FHIR server with Maven
  - Builds Docker images for FHIR server and demo client
  - Pushes images to GitHub Container Registry (ghcr.io)
  - Deploys to Kubernetes using Helm on push to main or version tags
  - Configurable via repository secrets and variables
- Helm chart for Kubernetes deployment (`chart/welldata-ephemeral-fhir-service/`)
  - FHIR server and demo client deployments
  - Configurable Solid provider URL via environment variable
  - Ingress support for both services
  - Uses solidcommunity.net as default Solid provider
- Demo client production Dockerfile with nginx
  - Dynamic `/config.js` endpoint for runtime configuration
  - Dynamic `/clientid.jsonld` for Solid-OIDC Client ID Document
  - Environment variable substitution at container startup
  - Proxy to FHIR backend via `/fhir/` path

### Changed

- Questionnaires are now served statically from local files (not session-scoped)
  - Questionnaire endpoint is now public (no authentication required)
  - Questionnaires are loaded at server startup and shared across all sessions
  - This aligns with the concept that Questionnaires are definitions, not user data
- Solid pod integration now enabled by default in Helm chart (`fhirServer.solid.enabled: true`)
- Updated WellData Implementation Guide package to v0.1.1
- Demo client now uses health-check-1-0 questionnaire instead of zipster
- Added Docker Compose `develop.watch` for automatic rebuild on file changes
- Demo client uses OIDC discovery for endpoint URLs
- Demo client serves dynamic Client ID Document at `/clientid.jsonld` for external Solid providers
  - Automatically adapts to deployment URL (works on localhost, ngrok, production, etc.)
  - Respects X-Forwarded-Proto and X-Forwarded-Host headers for reverse proxy setups
  - Solid-OIDC compliant for providers like solidcommunity.net
- Demo client default Solid provider is now configurable via `DEFAULT_SOLID_PROVIDER` environment variable

### Added

- Demo client: React application for Solid OIDC authentication and FHIR interactions
  - Solid OIDC authentication with dynamic client registration and PKCE
  - Questionnaire rendering with support for grouped items and multiple answer types
  - QuestionnaireResponse submission with linked Observation creation
  - History view grouped by QuestionnaireResponse with related Observations
  - Shared timestamp for QuestionnaireResponse and linked Observations
  - Configurable Solid provider URL (supports external providers like solidcommunity.net)

## [0.1.0] - 2025-11-28

### Added

- Initial implementation of the WellData Ephemeral FHIR Service
- In-memory FHIR R4 resource storage with session isolation
- Support for Patient, Observation, Questionnaire, and QuestionnaireResponse resources
- Session-scoped data storage tied to JWT access tokens
  - Sessions identified by JWT `jti` claim (or token hash as fallback)
  - Automatic session cleanup when JWT expires
  - Data loaded on-demand per session
- JWT access token authentication via Bearer token in Authorization header
  - Token decoding (without signature validation) for session scoping
  - Public endpoints (metadata, StructureDefinition, ImplementationGuide) accessible without authentication
- WellData Implementation Guide package loading from GitHub releases
  - Automatic download and parsing of IG `.tgz` package
  - StructureDefinition and ImplementationGuide resources served via FHIR API
- FHIR search parameters for all resource types
  - Patient: identifier, name, family, given, birthdate
  - Observation: subject, code, date, status, category
  - Questionnaire: identifier, name, title, status
  - QuestionnaireResponse: subject, questionnaire, status, authored, author
- Docker support with multi-stage build
- CORS support for cross-origin requests
- Response highlighting for browser-based API exploration

### Technical Stack

- Java 21
- Spring Boot 3.3.5
- HAPI FHIR 7.4.0
- Apache Jena 4.10.0 (for RDF/TTL support)
- Auth0 java-jwt 4.4.0 (for JWT decoding)
