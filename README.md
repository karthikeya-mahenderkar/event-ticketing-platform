# Event Ticketing Platform

A full-stack event-ticketing platform: organizers create events and ticket types, attendees buy
tickets and receive QR codes, and staff validate tickets at the door. The focus is **correctness under
concurrency**: the last ticket can never be sold twice, a retried purchase never buys twice, and a
ticket can never be admitted twice.

▶️ Interactive demo of the concurrency behaviour:
[karthikeya-mahenderkar.vercel.app](https://karthikeya-mahenderkar.vercel.app/#case-event-ticketing)

**Stack:** Java 21 · Spring Boot 3.4 · Spring Data JPA / Hibernate · PostgreSQL · Keycloak (OIDC/JWT) ·
ZXing · React + Vite · Docker Compose

> This project started from a public build tutorial and has since been extended. The concurrency
> work below (atomic inventory, idempotent purchases, race-free validation and the concurrency
> test suite) is my own. The original MIT licence notice is kept in [`LICENSE`](LICENSE).

## Concurrency guarantees

All three are proven by tests that fire real simultaneous requests
(`TicketConcurrencyTest` in [`backend/src/test`](backend/src/test)).

### 1. No overselling: atomic inventory reservation
Purchases reserve inventory with **one conditional UPDATE** instead of "lock row, count tickets, check":

```sql
UPDATE ticket_types SET sold_count = sold_count + 1
 WHERE id = ? AND sold_count < total_available
```

If it updates 0 rows, the ticket type is sold out. The database serialises concurrent updates of the
same row and re-checks the condition against the latest committed value, so `sold_count` can never
exceed `total_available`. There is no explicit lock and no `COUNT(*)` that grows with sales.
→ `TicketTypeRepository#reserveTicket`

**Test:** 20 buyers race for 5 tickets → exactly 5 succeed, 15 get `TicketsSoldOutException`.

### 2. No double purchase on retry: idempotency keys
Clients can send an `Idempotency-Key` header (e.g. one UUID per checkout attempt). The key is stored on
the ticket with a **unique constraint on `(purchaser_id, idempotency_key)`**:
- a sequential retry finds the existing ticket and returns it;
- concurrent retries race on the constraint: exactly one insert wins, and the losers' transactions roll
  back, *including their inventory reservation*. The controller answers them with the same success response.

→ `TicketTypeServiceImpl#purchaseTicket`, `TicketTypeController`

**Test:** the same purchase retried 5 times at once → exactly 1 ticket, `sold_count` = 1.

### 3. No double admission: race-free validation
Validating a ticket now locks its row (`SELECT … FOR UPDATE`) and checks for an earlier `VALID` scan with
a fresh query rather than a cached collection. Simultaneous scans of the same ticket run one after
another, and only the first is `VALID`.
→ `TicketRepository#findByIdForUpdate`, `TicketValidationServiceImpl`

**Test:** 10 staff scan the same ticket at once → exactly 1 `VALID`, 9 `INVALID`.
Without the lock, all 10 scans come back `VALID`.

## Other features
- **Role-based access** with Keycloak-issued JWTs: realm roles are mapped to Spring authorities;
  `ORGANIZER` manages events, `STAFF` validates tickets; users are provisioned from token claims on first request.
- **QR tickets** generated with ZXing from a random UUID (not the ticket ID), with QR or manual validation.
- Layered architecture with DTOs and MapStruct mappers at the API boundary, and centralised error handling.

## Running it

You'll need JDK 21, Node 20+ and Docker.

```bash
# PostgreSQL, Adminer and Keycloak, then the backend
cd backend
docker compose up -d
./mvnw spring-boot:run

# Frontend, in a second terminal
cd frontend
npm install
npm run dev
```

| Service    | URL                   |
| ---------- | --------------------- |
| Frontend   | http://localhost:5173 |
| Backend    | http://localhost:8080 |
| Keycloak   | http://localhost:9090 |
| Adminer    | http://localhost:8888 |
| PostgreSQL | localhost:5432        |

Keycloak needs an `event-ticket-platform` realm with an `event-ticket-platform-app` client, matching the
issuer URI in `backend/src/main/resources/application.properties` and the OIDC config in
`frontend/src/main.tsx`.

**Existing local database?** `sold_count` is a new column. Back-fill it once after upgrading:
```sql
UPDATE ticket_types tt SET sold_count = (SELECT COUNT(*) FROM tickets t WHERE t.ticket_type_id = tt.id);
```

## Tests

```bash
cd backend
./mvnw test
```

The tests run against an in-memory H2 database in PostgreSQL mode, so they need no Docker, PostgreSQL or Keycloak.

## Local development only
Not production-ready as configured: the Keycloak admin credentials in `docker-compose.yml` are the
defaults and the database password is committed in plain text.

## Licence
MIT. See [`LICENSE`](LICENSE).
