# FHIR API vs Direct Solid Pod Access

## Why a FHIR API layer on top of Solid pods?

Solid is an excellent technology for data sovereignty and decentralized storage, but it is a *storage layer*, not an *application layer*. FHIR provides the application semantics that healthcare applications require. The ephemeral FHIR service bridges these two worlds: FHIR's rich API for applications, Solid's data sovereignty for the citizen.

Importantly, FHIR is designed as an *exchange* format -- it is not intended for storage or fixed representation. Health data in Solid pods is stored as RDF/Turtle, which is the native format for Linked Data and Solid. The ephemeral FHIR service translates between these two representations: it reads RDF from the pod, exposes it as FHIR resources for applications to consume and manipulate, and writes changes back as RDF. This separation is by design -- each layer does what it does best.

## Arguments for a FHIR API layer

### 1. FHIR is the lingua franca of healthcare

FHIR and the surrounding exchange patterns are the universally adopted standard for exchanging healthcare data. It is mandated or recommended by national programs (MedMij, Koppeltaal, NICTIZ), international initiatives (IHE, HL7), and regulatory frameworks (European Health Data Space). By exposing a FHIR API, the citizen's data speaks the same language as the rest of the healthcare ecosystem -- all based on existing standards and methods like the FHIR REST and search API, no additional technology needed.

### 2. Fine-grained access control

FHIR supports resource-level authorization. Access can be controlled per resource type or even per element. For example, a patient may want to grant access to blood pressure measurements but not to mental health questionnaires. Solid's WAC/ACP operates at the document/container level without healthcare domain-specific granularity.

### 3. Decoupling of storage and representation

In Solid, the storage structure determines what you get back. Store data as a Bundle, and the response is a Bundle. Store it as 100 separate Turtle files, and that's what you get. FHIR decouples this: the server determines how data is stored, but the client can request exactly the representation it needs via search parameters, `_include`, `_revinclude`, and `_elements`.

### 4. Search and queries

FHIR offers standardized search parameters per resource type (e.g., `Observation?code=27113001&date=ge2025-01-01`), including chaining (`Observation?subject.name=Jansen`), composites, and modifiers. Solid does not provide a query interface -- running SPARQL against a pod is not part of the Solid Protocol specification and is not supported by most pod implementations.

### 5. Subscriptions and notifications

FHIR R4B/R5 Subscriptions provide a standardized mechanism for change notifications with semantic understanding of the content (e.g., "notify me when a new QuestionnaireResponse is created"). Solid only offers generic WebSocket notifications at the container level -- you know *that* something changed, but not *what*.

### 6. RESTful interface with domain operations

FHIR provides a full REST API with JSON and XML representations, `_format` parameter, and standardized operations (create, read, update, delete, search, history, batch/transaction). Solid's LDP interface is generic and lacks healthcare domain-specific operations such as `$validate` or `$everything`.

### 7. Profiles and validation

FHIR StructureDefinitions and Implementation Guides (such as the WellData IG) define exactly which fields are required, which terminology must be used, and how resources relate to each other. With direct Solid storage, there is no validation layer -- any client can write arbitrary RDF without any guarantee of structure or interoperability.

### 8. Interoperability with the existing ecosystem

Any existing FHIR client (EHRs, apps, tooling) can connect directly without knowing that a Solid pod is underneath. No Solid-specific integration is required.

### 9. History and versioning

FHIR provides `_history` at both the resource and server level, allowing clients to retrieve previous versions of a resource. Solid has no built-in versioning mechanism.

### 10. CapabilityStatement and discovery

A FHIR server advertises via `/metadata` exactly what it supports: which resource types, search parameters, and operations are available. Clients can automatically adapt to these capabilities. Solid has no equivalent for domain-specific capability discovery.

### 11. Transactions

FHIR Bundles of type `transaction` provide atomic operations across multiple resources. With direct Solid storage, there are no transaction guarantees -- if you need to update 5 files and it fails at the 3rd, you end up with an inconsistent state.

## The two patterns are not mutually exclusive

A data aggregator could be used to *retrieve* data from hospitals and place it in the citizen's Solid pod. The ephemeral FHIR service then sits on top as the API layer for applications.
