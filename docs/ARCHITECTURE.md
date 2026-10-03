# Architecture

## Overview
LoanFlow is a secure educational loan origination and approval platform. This project serves as a portfolio application and is not intended for real banking, regulated lending, KYC/AML compliance, or production financial operations. The underlying rules engine is deterministic for demo purposes, and payments are strictly simulated records with no real money movement.

## System Architecture
The backend is designed as a **Single Spring Boot Maven Application** following a **Package-by-Feature (Modular Monolith)** pattern. 
This structure enforces strict separation of concerns within a unified codebase, allowing the application to be deployed as a single artifact while keeping domain boundaries clear for a potential future extraction into microservices.

## Technology Stack
- **Backend:** Java 21, Spring Boot, Spring Security, JWT, MySQL 8 (InnoDB), Spring Data JPA, Hibernate, Flyway
- **Caching & Async (Future):** Redis, Apache Kafka, Transactional Outbox
- **Storage:** Private S3-compatible storage
- **Testing:** JUnit 5, Mockito, Testcontainers
- **Frontend:** React with Vite, JavaScript, Plain CSS
- **DevOps:** Docker, OpenAPI, GitHub Actions

## Module Boundaries (Package-by-Feature)
The application is strictly partitioned into domain-specific packages:
- `auth`: JWT generation & validation, login/register endpoints.
- `user`: User profiles, KYC data placeholders, and role mappings.
- `product`: Loan product configurations (rates, terms).
- `application`: Loan application tracking and state machine.
- `document`: S3 storage integration, document upload/download handling.
- `eligibility`: Deterministic demo rules engine.
- `review`: Credit and Manager review processes.
- `loan`: Active loan servicing and management.
- `payment`: Simulated payment and disbursement records.
- `notification`: Sync notifications (Kafka reserved for later phases).
- `audit`: Audit trail logging (Kafka reserved for later phases).

## Communication Strategy
For Phase 0 and initial implementation, cross-module communication is completely **synchronous**, utilizing standard Spring `@Service` interface calls and MySQL transactions. 
Modules interact via strictly defined interfaces. Direct access to another module's database tables or repositories is strictly forbidden.
Kafka, transactional outbox, and asynchronous communication patterns are scheduled for later phases.
