# Event Ticket App

> **About this repo.** I built this project by following Devtiro's published, step-by-step
> Event Ticket Platform build guide (included in [`docs/`](docs/event-ticket-app-combined.md)).
> I implemented and ran every backend layer myself; the React frontend was provided by the guide.
> Interactive demo of the concurrency behaviour on my portfolio:
> [karthikeya-mahenderkar.vercel.app](https://karthikeya-mahenderkar.vercel.app/#case-event-ticketing)

## What I implemented

- **Spring Boot REST backend** in a layered architecture (controllers → services → repositories)
  for events, ticket types, purchases, QR codes and ticket validation.
- **Overselling protection:** `purchaseTicket()` runs in a transaction and loads the ticket type with
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT … FOR UPDATE`), so concurrent buyers of the last
  ticket are serialised and the loser gets `TicketsSoldOutException`.
- **Security:** Keycloak-issued JWTs, roles mapped from `realm_access`, `ORGANIZER` and `STAFF`
  restricted endpoints, and users provisioned from token claims on first request.
- **QR tickets:** random-UUID QR codes rendered with ZXing; QR or manual validation where a second
  scan of an already-validated ticket is recorded as `INVALID`.
- Ran the full stack locally with **PostgreSQL, Adminer and Keycloak in Docker Compose**.

## What I'd change next

- **Validation race:** two simultaneous scans of the same ticket could both be marked `VALID`.
  Lock the ticket row during validation, or add a partial unique index allowing one `VALID` scan per ticket.
- **First-login race:** two concurrent first requests could both insert the same user; use an upsert.
- **Counting on every purchase:** replace `countByTicketTypeId` with a `sold` counter and an atomic
  `UPDATE … WHERE sold < total_available`.
- **Retries:** accept an idempotency key on purchase so a client retry can't buy twice.

---

<!-- This is the backend and frontend source code for the Devtiro event ticket app build published June
2025. -->

The Event Ticket Platform is a Spring Boot application that lets organizers create events and sell
tickets, event goers buy those tickets, and staff scan ticket QR codes at the event. Authentication
is handled by Keycloak. The frontend is a React application built with Vite.

<!-- You can check out the build video [here](https://youtu.be/vK-KQZ8cpbU). -->

## The Build Guide

The full step-by-step write-up of the build lives in
[docs/event-ticket-app-combined.md](docs/event-ticket-app-combined.md). It covers the project brief,
the user stories and UI designs, the domain and architecture design, and then builds the backend up
endpoint by endpoint.

## What's In This Repo

| Directory   | Contents                                                          |
| ----------- | ----------------------------------------------------------------- |
| `docs/`     | The build guide, with its UI designs and diagrams                 |
| `backend/`  | The Spring Boot backend, as built in the video                    |
| `frontend/` | The React frontend, provided for you to interact with the backend |

## Running It

You'll need JDK 21, Node 20 or later, and Docker.

```bash
# Start PostgreSQL, Adminer and Keycloak, then run the backend
cd backend
docker compose up -d
./mvnw spring-boot:run

# Run the frontend, in a second terminal
cd frontend
npm install
npm run dev
```

The frontend runs on http://localhost:5173 and proxies API calls through to the backend on port 8080.
The supporting services come up on these ports:

| Service    | URL                   |
| ---------- | --------------------- |
| Keycloak   | http://localhost:9090 |
| Adminer    | http://localhost:8888 |
| PostgreSQL | localhost:5432        |

Keycloak needs an `event-ticket-platform` realm containing an `event-ticket-platform-app` client, to
match the issuer URI configured in the backend and the OIDC config in `frontend/src/main.tsx`. The
guide walks through setting this up.

<!-- > [!WARNING]
> This project is pinned to **Java 21** and **Spring Boot 3.4.4**. It was built and tested against
> those versions only. Newer versions of Spring Boot and Java may require changes to the code and
> configuration in this repo, and the guide's instructions may no longer match what you see.

> [!CAUTION]
> **This is intended for your local development environment only.** It is not production-ready. The
> Keycloak admin credentials in `docker-compose.yml` are the default `admin`/`admin`, and the
> database password is committed in plain text.

> [!IMPORTANT]
> This is a teaching project. It's written to demonstrate how a Spring Boot application is designed
> and built, following the same practices I'd use on real work — a layered architecture, DTOs and
> mappers at the API boundary, constructor injection, and centralised error handling.
>
> That said, it was built live, and its scope is deliberately limited to what the build covers. It
> may contain bugs, and some things a production system would need — comprehensive input validation,
> a full test suite, hardened infrastructure config — are out of scope by design rather than by
> oversight. -->

<!-- ## Licence

This repo is licensed in two parts.

**The code** — `backend/` and `frontend/` — is [MIT licensed](LICENSE). Use it, change it, build on
it, no strings attached.

**The build guide** — everything in [`docs/`](docs/LICENSE) — is licensed under
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). You're welcome to share it,
translate it, and build on it, but please credit Devtiro, don't sell it or put it behind a paywall,
and keep any adaptations under the same licence. -->

<!-- Copyright © 2025 Devtiro Ltd. -->
