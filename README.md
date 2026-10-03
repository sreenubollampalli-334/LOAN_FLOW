# LoanFlow — Loan Application & Management Platform

LoanFlow is a Java Full-Stack loan management application that simulates the complete lifecycle of a loan, from customer application and document submission to credit review, manager approval, disbursement, and repayment.

## Technology Stack

- Java 21
- Spring Boot
- Spring Security + JWT
- React.js + JavaScript
- MySQL 8
- Spring Data JPA / Hibernate
- Redis
- Apache Kafka
- Flyway
- Docker / Docker Compose
- JUnit 5 / Mockito / Testcontainers
- REST APIs
- OpenAPI / Swagger

## Main Roles

- CUSTOMER
- CREDIT_OFFICER
- MANAGER
- ADMIN

## Complete Loan Lifecycle

```text
CUSTOMER
   |
   v
DRAFT
   |
   | Submit
   v
SUBMITTED
   |
   v
CREDIT OFFICER REVIEW
   |
   +-- RECOMMEND
   +-- DO_NOT_RECOMMEND
   +-- REQUEST_INFORMATION
   |
   v
MANAGER REVIEW
   |
   +-- APPROVE
   +-- REJECT
   +-- RETURN_FOR_REVIEW
   |
   v
APPROVED
   |
   v
ADMIN DISBURSEMENT
   |
   v
DISBURSED
   |
   v
REPAYMENT
   |
   v
PAYMENT ALLOCATION
   |
   v
CLOSED
```

## Customer Features

- Registration and login
- Profile management
- Browse loan products
- Create loan applications
- Save applications as drafts
- Update draft/returned applications
- Upload required documents
- Submit and resubmit applications
- View application status
- View approved loans
- View repayment schedules
- View payment history
- Make loan payments
- View repayment summaries
- Track fully paid loans

## Credit Officer Features

- View submitted applications
- Review applications
- Review uploaded documents
- Submit credit recommendations
- Recommend applications
- Do not recommend applications
- Request additional information
- Participate in subsequent review cycles

## Manager Features

- View applications ready for management review
- Review credit recommendations
- Approve applications
- Reject applications
- Return applications for further review
- Create the corresponding Loan after approval

## Admin Features

- View approved loans waiting for disbursement
- View disbursement queue
- View disbursement details
- Initiate simulated loan disbursement
- View disbursement attempts
- Monitor administrative operations

Successful disbursement changes the loan status to DISBURSED and generates the repayment schedule.

## Payment & Repayment

```text
APPROVED
   |
   v
DISBURSED
   |
   v
Repayment Schedule Generated
   |
   v
Pending Installments
   |
   v
Customer Payment
   |
   v
Payment Allocation
   |
   v
Installment Paid
   |
   v
All Installments Paid
   |
   v
CLOSED
```

Payment capabilities include:

- Payment creation
- Payment history
- Payment details
- Payment allocation
- Idempotency
- Refund processing
- Partial payments
- Full installment payments
- Automatic loan closure
- Payment audit logging
- Payment outbox events

Payments are accepted only for DISBURSED loans.

## Security

LoanFlow uses Spring Security and JWT authentication.

Security includes:

- JWT authentication
- Role-based authorization
- Protected REST APIs
- `@PreAuthorize`
- Customer ownership validation
- Staff role validation
- Protected frontend routes
- Unauthorized access handling
- JWT expiration handling
- Idempotency protection for financial operations

## Architecture

```text
                    +---------------------+
                    |      React UI       |
                    |      Frontend       |
                    +----------+----------+
                               |
                               | REST APIs
                               v
                    +---------------------+
                    |    Spring Boot      |
                    |      Backend        |
                    +----------+----------+
                               |
             +-----------------+-----------------+
             |                 |                 |
             v                 v                 v
        +---------+       +---------+       +---------+
        |  MySQL  |       |  Redis  |       |  Kafka  |
        +---------+       +---------+       +---------+

                    +---------------------+
                    |      Flyway         |
                    | Database Migration  |
                    +---------------------+
```

## Backend Package Structure

The backend follows a package-by-feature architecture.

```text
com.loanflow
├── auth
├── user
├── customer
├── loan
├── application
├── document
├── review
├── repayment
├── payment
├── disbursement
├── audit
├── outbox
├── security
└── common
```

## Database

LoanFlow uses MySQL with Flyway migrations.

Major database areas include:

- users
- roles
- user_roles
- customer_profiles
- loan_products
- product_required_documents
- loan_applications
- application_status_history
- documents
- document_reviews
- eligibility_assessments
- eligibility_reason_codes
- credit_reviews
- manager_reviews
- loans
- repayment_schedules
- repayment_installments
- payments
- payment_allocations
- refund_allocations
- disbursements
- disbursement_attempts
- audit_logs
- outbox_events

## Concurrency Control

LoanFlow uses both optimistic and pessimistic locking.

### Optimistic Locking

Important entities use:

```java
@Version
private Integer version;
```

### Pessimistic Locking

Critical operations use database row locking, including:

- Manager review
- Credit officer review
- Payment allocation
- Payment refunds
- Repayment schedule allocation

Example:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

These controls protect important financial and lifecycle transitions from concurrent updates.

## Idempotency

Financial operations use idempotency keys.

Payment requests require:

```text
Idempotency-Key
```

The database also enforces uniqueness for customer payment idempotency keys.

This protects against duplicate requests caused by double clicks, network retries, and duplicate HTTP requests.

## Audit Logging

Important business actions are recorded in audit logs.

Examples:

- APPLICATION_SUBMITTED
- CREDIT_REVIEW_CREATED
- MANAGER_REVIEW_CREATED
- LOAN_APPROVED
- LOAN_DISBURSED
- PAYMENT_CREATED
- PAYMENT_REFUNDED

## Transactional Outbox

LoanFlow implements the Transactional Outbox Pattern.

```text
Business Transaction
        |
        +-- Update Business Data
        |
        +-- Audit Log
        |
        +-- Outbox Event
                |
                v
              Kafka
```

This helps maintain consistency between database transactions and asynchronous event processing.

## Important Business Rules

### Application

- Customers can edit applications in DRAFT.
- Customers can edit returned applications when additional information is required.
- Submitted applications are locked for customer modification.
- Applications must belong to the authenticated customer.
- Applications must be submitted before staff review.

### Credit Review

- Credit Officers can review eligible submitted applications.
- Duplicate review scenarios are controlled.
- REQUEST_INFORMATION can return an application to the customer workflow.
- Resubmission enables a new review cycle.

### Manager Review

- Managers perform the final application decision.
- Manager operations use row-level locking.
- Approval creates the corresponding Loan.
- Returned applications can enter a subsequent review cycle.

### Disbursement

- Only approved loans can enter disbursement.
- Successful disbursement changes the loan to DISBURSED.
- Repayment schedules are generated after successful disbursement.

### Payments

- Payments are accepted only for DISBURSED loans.
- Payment requests require idempotency protection.
- Payments are allocated against repayment installments.
- Fully paid loans transition to CLOSED.
- Payment processing uses locking to protect against concurrent allocation.

## Project Structure

```text
LOAN_FLOW/
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   ├── components/
│   │   ├── context/
│   │   ├── layouts/
│   │   ├── pages/
│   │   ├── routes/
│   │   └── ...
│   ├── package.json
│   └── vite.config.js
│
├── loanflow-backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── pom.xml
│   └── docker-compose.yml
│
├── docs/
├── .gitignore
└── README.md
```

## Running Locally

LoanFlow is intended for local development and college/personal project demonstration.

### Prerequisites

- Java 21
- Node.js
- npm
- Maven
- MySQL 8
- Docker Desktop
- Git

### 1. Clone Repository

Replace the placeholder with your GitHub repository URL.

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd LOAN_FLOW
```

### 2. Start Infrastructure

```bash
cd loanflow-backend
docker compose up -d
```

Required infrastructure includes:

- MySQL
- Redis
- Kafka

Check containers:

```bash
docker ps
```

### 3. Configure Environment Variables

Typical variables include:

```text
DB_PASSWORD
JWT_SECRET
S3_ACCESS_KEY
S3_SECRET_KEY
```

Never commit real secrets to GitHub.

### 4. Start Backend

From `loanflow-backend`:

```bash
mvn spring-boot:run
```

Backend:

```text
http://localhost:8080
```

### 5. Start Frontend

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

## Verification

Frontend lint:

```bash
npm run lint
```

Frontend build:

```bash
npm run build
```

Backend compilation:

```bash
mvn clean compile
```

Backend tests:

```bash
mvn clean test
```

Integration tests require the required infrastructure services to be running.

## Recommended Demo Workflow

```text
1. Register Customer
2. Create Loan Application
3. Save as Draft
4. Upload Required Documents
5. Submit Application
6. Login as Credit Officer
7. Review Application
8. Recommend or Request Information
9. Login as Manager
10. Approve Application
11. Login as Admin
12. Disburse Loan
13. Repayment Schedule Generated
14. Login as Customer
15. Make EMI Payment
16. Payment Allocation
17. Repeat Until Fully Paid
18. Loan CLOSED
```

## Project Highlights

### Backend Engineering

- REST API design
- Layered architecture
- Package-by-feature architecture
- Spring Security
- JWT authentication
- Role-based authorization
- Transaction management
- JPA/Hibernate
- Database constraints
- Flyway migrations

### Financial Workflow Engineering

- Loan state transitions
- Payment allocation
- Refund processing
- Idempotency
- Repayment schedules
- Loan closure
- Concurrency handling

### Distributed-System Concepts

- Kafka
- Transactional Outbox
- Redis
- Event-driven architecture

### Reliability

- Optimistic locking
- Pessimistic locking
- Transaction boundaries
- Audit logging
- Idempotency protection

## Current Project Status

| Area | Status |
|---|---|
| React Frontend | Complete |
| Spring Boot Backend | Complete |
| JWT Authentication | Complete |
| Role-Based Authorization | Complete |
| Loan Application | Complete |
| Credit Review | Complete |
| Manager Review | Complete |
| Loan Approval | Complete |
| Admin Disbursement | Complete |
| Repayment Schedule | Complete |
| Payment Processing | Complete |
| Refund Processing | Complete |
| Loan Closure | Complete |
| Audit Logging | Complete |
| Transactional Outbox | Complete |
| Concurrency Protection | Complete |
| Flyway Migrations | Complete |
| Docker Infrastructure | Configured |
| Cloud Deployment | Not planned |

## Known Limitations

LoanFlow is primarily a local college/personal project.

Current limitations include:

- The application is not deployed to a public production environment.
- Local Docker infrastructure is required for the complete backend environment.
- Integration tests require MySQL/Testcontainers infrastructure to be available.
- S3/MinIO infrastructure is not included as a dedicated service in the current Docker Compose configuration.
- Some frontend React hook warnings may remain from existing data-fetching patterns.
- Payment and disbursement operations simulate external financial systems rather than connecting to real banking/payment providers.

## Project Purpose

LoanFlow was developed as a Java Full-Stack engineering project to understand and demonstrate how a real-world loan management system can be designed.

The project focuses on:

- Business rules
- State transitions
- Secure APIs
- Database design
- Transaction management
- Concurrency
- Financial workflows
- Event-driven architecture
- Full-stack integration

Rather than implementing only CRUD operations, LoanFlow models a complete business workflow with explicit authorization, transaction boundaries, auditability, and consistency controls.

## Author

**Sreenu Bollampalli**

Computer Science Undergraduate  
Java Full-Stack Developer | Software Engineer | DSA Enthusiast

### Technologies

```text
Java
Spring Boot
Spring Security
JWT
React
JavaScript
MySQL
JPA / Hibernate
Redis
Kafka
Flyway
Docker
JUnit
Mockito
REST APIs
```

## Disclaimer

LoanFlow is an educational and personal project.

It does not connect to real banking systems, real payment gateways, or real financial institutions. Disbursement and payment operations are implemented for application demonstration and learning purposes.
