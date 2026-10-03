# Data Model and Security Guidelines

## Data Model Rules
- **Single Database:** The system utilizes a single MySQL 8 database instance.
- **Module Ownership:** Every database table must be logically owned by a specific module (e.g., `loan_applications` belongs to the `application` module).
- **Foreign Keys:** Standard MySQL foreign keys are permitted to enforce data integrity at the database level.
- **Cross-Module Access:** Accessing another module's data must be done exclusively via service interfaces (e.g., `ApplicationService` calling `UserService.getUserById(id)`). Direct cross-module SQL joins or accessing repositories belonging to other modules is prohibited.

## Concurrency Protection
- **Optimistic Locking:** All critical entities (especially Loan Applications, Loans, and Payments) must implement optimistic locking using JPA's `@Version` annotation. This protects against lost updates and race conditions during concurrent modifications.

## Security & IDOR Prevention
- **Authentication:** All secured endpoints must validate a valid JWT provided in the `Authorization: Bearer <token>` header.
- **IDOR (Insecure Direct Object Reference) Protection:** Object ownership must be strictly validated at the API/Service level.
  - When a `CUSTOMER` requests `GET /api/v1/applications/{id}`, the system must verify that the `customerId` associated with the application matches the authenticated principal.
  - Documents stored in S3 must not be publicly accessible. The `document` module will handle fetching and verifying access rights before serving documents or generating pre-signed URLs.
