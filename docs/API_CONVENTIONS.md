# API Conventions

## RESTful Standards
- Base path for all endpoints: `/api/v1/`
- Resource names should be plural nouns (e.g., `/api/v1/applications`, `/api/v1/users`).
- HTTP verbs denote actions: `GET` (read), `POST` (create), `PUT` (full update), `PATCH` (partial update), `DELETE` (remove).

## Standard JSON Response Envelopes
Responses should follow a predictable envelope structure, especially for errors.

**Success Response (Example):**
```json
{
  "data": {
    "id": 1,
    "status": "SUBMITTED"
  },
  "timestamp": "2026-09-26T18:13:15Z"
}
```

**Error Response (Example):**
```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Invalid input provided",
    "details": [
      {
        "field": "amount",
        "issue": "Must be greater than zero"
      }
    ]
  },
  "timestamp": "2026-09-26T18:13:15Z"
}
```

## Pagination
List endpoints must support pagination using standard query parameters:
- `page`: The 0-indexed page number (default: 0).
- `size`: The number of records per page (default: 20).
- `sort`: Field and direction to sort by (e.g., `createdAt,desc`).

Paginated responses will include a `page` metadata block containing `totalElements`, `totalPages`, `number`, and `size`.

## Headers
- `Authorization`: Used for passing the JWT token (`Bearer <token>`).
- `Content-Type`: Set to `application/json` for request and response payloads.
