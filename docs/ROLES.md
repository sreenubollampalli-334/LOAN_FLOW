# Roles and Responsibilities

The system implements a strict Role-Based Access Control (RBAC) model with the following exact roles:

## 1. CUSTOMER
- Can view available loan products.
- Can create, edit (when in DRAFT), and submit their own loan applications.
- Can upload required documents for their applications.
- Can view their active loans and simulated payment history.
- Cannot view or access applications, documents, or data belonging to other customers.

## 2. CREDIT_OFFICER
- Responsible for the initial review phase of submitted applications.
- Performs document verification.
- Executes the application credit review (after eligibility checks).
- Can transition applications to `CREDIT_REVIEW`, request additional info (`ADDITIONAL_INFO_REQUIRED`), or push to `MANAGER_REVIEW` upon successful verification.

## 3. MANAGER
- Responsible for the final decision on loan applications.
- Performs the final approval or rejection after all prerequisites (credit review) are met.
- Can transition applications from `MANAGER_REVIEW` to `APPROVED` or `REJECTED`.

## 4. ADMIN
- Manages system configuration and parameters.
- Manages users and assigns roles.
- Configures loan products (interest rates, terms, limits).
- Cannot directly approve applications or originate loans.

## 5. AUDITOR
- Has authorized read-only access to audit logs, application history, and system trails.
- Cannot modify any data or perform state transitions on applications or loans.
