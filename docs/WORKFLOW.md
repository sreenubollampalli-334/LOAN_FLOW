# Application Workflow

## Application Lifecycle
The core of the LoanFlow system is a strict state machine governing loan applications. Transitions must be authorized based on the user's role. 

The exact application lifecycle is as follows:
`DRAFT` → `SUBMITTED` → `DOCUMENT_VERIFICATION` → `ELIGIBILITY_CHECK` → `CREDIT_REVIEW` → `MANAGER_REVIEW` → `APPROVED` → `DISBURSED` → `ACTIVE` → `CLOSED`

## Exception States
- `ADDITIONAL_INFO_REQUIRED`: Can be triggered during `DOCUMENT_VERIFICATION` or `CREDIT_REVIEW` if documents are missing or invalid. The application returns to the customer for updates before resubmission.
- `REJECTED`: A final terminal state. Can be triggered during `ELIGIBILITY_CHECK`, `CREDIT_REVIEW`, or `MANAGER_REVIEW`.

## Transition Rules
- **State Changes:** Transitions must happen in order. Skipping mandatory steps is not permitted.
- **Reason Recording:** Any transition to an exception state (`ADDITIONAL_INFO_REQUIRED`, `REJECTED`) must be accompanied by a recorded reason to ensure traceability and auditability.
- **History Tracking:** Every state transition must be recorded in an application history log with a timestamp and the user ID of the actor performing the transition.
- **Concurrency Protection:** The system must implement optimistic locking to prevent race conditions during concurrent updates (e.g., two officers reviewing the same application simultaneously).
