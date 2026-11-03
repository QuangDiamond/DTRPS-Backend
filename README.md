# DTRPS Backend

## 1. Overview

DTRPS (Travel Discovery & Planning) uses PostgreSQL with pgvector for storing application data and supporting vector-based place discovery.

The project is code-first: JPA entities define the schema and Hibernate creates it at startup. See [Code-First Schema Changes](#8-code-first-schema-changes).

**Technology Stack**
- Java 21
- Spring Boot 3.5.6
- Maven
- PostgreSQL 17
- pgvector
- Docker Compose

## 2. Prerequisites

Install the following tools:

- Git
- Java JDK 21
- Apache Maven
- Docker Desktop

Ensure Docker Desktop is running before proceeding.

## 3. Clone Repository

```bash
git clone <BACKEND_REPOSITORY_URL>
cd DTRPS-Backend
```

Replace `<BACKEND_REPOSITORY_URL>` with the team's actual GitHub repository URL.

## 4. Database

By default the app connects to the team's shared PostgreSQL on the server (`72.61.114.184:5433`, database `dtrps`). The connection is in `src/main/resources/application.yaml`, so no setup is needed.

This is the same database the live API at `https://travel-api.fptuany.online` uses:

- Data you create or delete locally is real data.
- With the default `DDL_AUTO=update`, a new entity or column you run locally is added to the shared schema for everyone. Hibernate only adds, it never drops or renames.
- The DB account is a superuser and the connection has no SSL. Keep this source private.

To work on a separate local database instead, start the one in `compose.yaml` and point the app at it:

```bash
docker compose up -d
```

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/dtrps"
$env:DB_PASSWORD = "local_dev_only"
```

## 5. Configure Environment Variables

None are required. `.env.example` lists every option.

`JWT_SECRET` has a development default. The server sets its own, so tokens issued by a local run are rejected by the live API and the other way round. It needs at least 32 characters, and changing it invalidates all access tokens.

To create an admin at startup, set `ADMIN_EMAIL` and `ADMIN_PASSWORD`. On the shared database this creates a real admin, so only do it on a local database. Public registration never grants the ADMIN role.

## 6. Run Backend

```powershell
mvn spring-boot:run
```

If port 8080 is occupied:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

At startup the app runs these steps on an empty or existing database:

1. `schema.sql` enables the pgvector extension.
2. Hibernate creates missing tables, columns and constraints from the entities.
3. `db/post-schema.sql` adds partial unique indexes, the vector index, one composite foreign key and the summary views.
4. `db/reference-data.sql` adds roles, provinces, categories, interests, transport modes and special needs that are missing.
5. The first admin is created if `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set.

Steps 3 and 4 repeat on every start and change nothing when already applied.

## 7. Verify Database

Connect to the shared database with any PostgreSQL client using the values in `application.yaml`, or to the local one with:

```bash
docker compose exec db psql -U dtrps -d dtrps
```

Check database table count:

```sql
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'public'
AND table_type = 'BASE TABLE';
```

The current schema contains 45 tables and 2 views, `itinerary_day_summary` and `itinerary_cost_summary`.

Exit PostgreSQL:

```text
\q
```

## 8. Code-First Schema Changes

Change the entity under `modules/<module>/entity` and restart. Constraints and indexes live on the entity too, through `@Check`, `@Table(uniqueConstraints, indexes)` and `@ColumnDefault`.

`DDL_AUTO` controls what Hibernate may do at startup:

| Value | Behaviour | Use |
|---|---|---|
| `update` (default) | Adds missing tables and columns | Development, first run on an empty database |
| `validate` | Changes nothing, stops the app if entities and database disagree | Production |

`update` only adds. It does not rename or drop columns, change a column type, or add a `@Check` to a table that already exists. Those changes need a manual `ALTER TABLE` on every existing database, in the same release. Back up first.

## 9. Database Development Rules

1. Put schema changes on the entity, not directly in the database.
2. Put objects entities cannot express in `db/post-schema.sql`. Every statement must be safe to repeat.
3. Put fixed reference data in `db/reference-data.sql`. Only add rows there with `ON CONFLICT DO NOTHING`.
4. Do not commit production credentials or private environment files.
5. Do not delete the PostgreSQL Docker volume unless intentionally resetting local data.

## 10. Project Structure

Each module under `com.dtrps.modules` uses three layers:

```text
modules/<module>/
  controller/   HTTP only: routes, validation, status codes
  service/      Business rules and transactions
  repository/   Data access through Spring Data JPA
  entity/       JPA entities
  dto/          Request and response records
```

Controllers call services and services call repositories. Every table has an entity and a repository. The `auth` and `user` modules have all layers. Add `service/` and `controller/` to the other modules when their features are built.

## 11. Authentication and Authorization

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create a traveller account, returns 201 and tokens |
| POST | `/api/auth/login` | Public | Returns tokens |
| POST | `/api/auth/refresh` | Public | Exchange a refresh token for a new pair |
| POST | `/api/auth/revoke` | Public | Send `refreshToken` to end that session, also after the access token expires |
| POST | `/api/auth/logout` | Bearer | End all sessions of the user |
| POST | `/api/auth/password/change` | Bearer | Send `currentPassword` and `newPassword`, ends all sessions |
| GET | `/api/users/me` | Bearer | Current user |
| PUT | `/api/users/me` | Bearer | Update name, phone, date of birth, avatar |
| GET | `/api/users/me/preferences` | Bearer | Travel preferences |
| PUT | `/api/users/me/preferences` | Bearer | Replace travel preferences |
| GET | `/api/admin/users?page=0&size=20` | ADMIN | Newest first, `size` up to 100. Optional `keyword` (name, email, phone), `role` (TRAVELLER, BUSINESS_OWNER, ADMIN), `status` (ACTIVE, SUSPENDED). Items include `createdAt` and `statusNote` (lock reason) |
| GET | `/api/admin/users/stats` | ADMIN | Totals by status and role, new this month |
| GET | `/api/admin/users/{id}` | ADMIN | One user, with `statusNote` while suspended |
| PUT | `/api/admin/users/{id}/status` | ADMIN | Set `ACTIVE` or `SUSPENDED`; optional `reason` is kept in the audit log |
| POST | `/api/business-profile` | Bearer | Register own business profile (`businessName`, `taxCode`, `contactEmail`, `contactPhone`), starts `PENDING` |
| GET | `/api/business-profile` | Bearer | Own business profile; `statusNote` holds the admin's reason when rejected or locked |
| PUT | `/api/business-profile` | Bearer | Edit own profile; a rejected (`INACTIVE`) profile goes back to `PENDING` |
| GET | `/api/admin/business-profiles?status=&keyword=&page=0&size=20` | ADMIN | Paginated business profiles, newest first |
| GET | `/api/admin/business-profiles/stats` | ADMIN | Counts: `total`, `pending`, `active`, `suspended`, `rejected`, `newThisMonth` |
| GET | `/api/admin/business-profiles/{id}` | ADMIN | One business profile |
| POST | `/api/admin/business-profiles/{id}/approve` | ADMIN | `PENDING` → `ACTIVE`, grants `BUSINESS_OWNER` (owner sees it after refresh or login) |
| POST | `/api/admin/business-profiles/{id}/reject` | ADMIN | `PENDING` → `INACTIVE`, needs `reason` |
| PUT | `/api/admin/business-profiles/{id}/status` | ADMIN | Lock (`SUSPENDED`, needs `reason`) or unlock (`ACTIVE`) |
| GET | `/api/reference/provinces` | Bearer | 34 provinces `{code, name}`, sorted by name |
| GET | `/api/reference/categories` | Bearer | 4 service groups, each with its active `subtypes` |
| POST | `/api/business-listing` | Bearer | ACTIVE business submits its one place (`name`, `categoryCode` = subtype, `provinceCode`, `address`, `latitude`, `longitude`, optional `description`, `phone`, `website`, `openingHours[]` `{dayOfWeek 1-7, openTime, closeTime, closed}`, `prices[]` `{priceType, amount, currency, description}`, `images[]` `{url https, primary}` up to 10): listing `PENDING`, place `DRAFT` |
| GET | `/api/business-listing` | Bearer | Own place; `statusNote` holds the rejection reason |
| PUT | `/api/business-listing` | Bearer | Edit while `PENDING` or `REJECTED` (a rejected one goes back to `PENDING`); approved places change through revisions |
| POST | `/api/business-listing/revisions` | Bearer | Propose a change to the approved place (same body as the listing); the public place is unchanged until approved. One `PENDING` revision at a time |
| GET | `/api/business-listing/revisions` | Bearer | Own revisions, newest first, with `rejectReasonCode` and `partnerFeedback` |
| POST | `/api/business-listing/revisions/{id}/cancel` | Bearer | Withdraw a `PENDING` revision → `CANCELLED` |
| GET | `/api/admin/listing-revisions?status=&keyword=&page=0&size=20` | ADMIN | Revision queue, newest first |
| GET | `/api/admin/listing-revisions/{id}` | ADMIN | `{ revision, current }`: the proposal next to the public place |
| POST | `/api/admin/listing-revisions/{id}/approve` | ADMIN | Copies the proposal (incl. hours, prices, photos) to the place; optional internal `note` |
| POST | `/api/admin/listing-revisions/{id}/reject` | ADMIN | Needs `reasonCode` (`pricing`, `location`, `photos`, `duplicate`, `other`), `partnerFeedback` and `note` (required review note, stored in `review_note`, internal) |
| POST | `/api/uploads/images` | BUSINESS_OWNER, ADMIN | Multipart `file` (jpg/png/webp checked by content, max 5MB). Stores it on Cloudinary and returns `{ url }`. Needs `CLOUDINARY_URL` (env or gitignored `.env.local`); without it the endpoint answers 503 |
| GET | `/api/admin/places` | ADMIN | Every place (platform POIs + partner places), newest update first. Filters: `keyword`, `group`, `province`, `status` (DRAFT, ACTIVE, PAUSED, MAINTENANCE), `source` (PARTNER, ADMIN), `page`, `size`. Rows carry business, listing status, cover, photo count and rating |
| GET | `/api/admin/places/stats` | ADMIN | Totals by status and source, new this month, counts per group |
| GET | `/api/admin/places/{id}` | ADMIN | `{ summary, description, latitude, longitude, phone, website, openingHours, prices, images }` |
| GET | `/api/admin/business-listings?status=&keyword=&page=0&size=20` | ADMIN | Paginated listings, newest first |
| GET | `/api/admin/business-listings/stats` | ADMIN | Counts: `total`, `pending`, `approved`, `rejected`, `suspended` |
| GET | `/api/admin/business-listings/{id}` | ADMIN | One listing |
| POST | `/api/admin/business-listings/{id}/approve` | ADMIN | `PENDING` → `APPROVED`, place becomes `ACTIVE` (business must be `ACTIVE`) |
| POST | `/api/admin/business-listings/{id}/reject` | ADMIN | `PENDING` → `REJECTED`, needs `reason` |
| GET | `/api/health` | Public | Health check |

Swagger UI is at `/swagger-ui/index.html` and the OpenAPI document at `/v3/api-docs`. To call protected routes, log in, press **Authorize** and paste the `accessToken`.

Login and register return:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": 1, "name": "...", "email": "...", "phoneNumber": null, "dateOfBirth": null, "avatarUrl": null, "status": "ACTIVE", "roles": ["TRAVELLER"] },
  "admin": { "name": "...", "email": "...", "role": "ADMIN" }
}
```

`admin` is the shape Admin Web expects and appears only for admin accounts. Errors return `{ "message": "..." }`, plus `errors` by field on validation failures.

- Send `Authorization: Bearer <accessToken>`. Access tokens last 15 minutes by default.
- Every refresh returns a new refresh token, so clients must store it. Refresh tokens last 30 days by default.
- Reusing an old refresh token ends that login session on every device using it.
- Everything is denied by default. Protect a route with `@PreAuthorize("hasRole('ADMIN')")`. Roles are TRAVELLER, BUSINESS_OWNER and ADMIN.
- Public auth routes allow 20 requests per minute per IP and route. Behind a reverse proxy, configure `server.forward-headers-strategy` and trusted proxies first, or all users share one limit.
- 10 failed logins lock that email for 15 minutes. Both limits are kept in memory per app instance and reset on restart.

Not implemented yet: forgot password, email verification, 2FA.

## 12. Tests

Tests need Docker because integration tests start `pgvector/pgvector:pg17` through Testcontainers.

```powershell
mvn verify
```

The coverage report is written to `target/site/jacoco/index.html`.

## 13. Important Business Rules

- A user may have multiple roles, including Traveller and Business Owner.
- Each Business Owner manages at most one business listing.
- Changes to business listing information require Admin approval before publication.
- A business listing can have multiple advertisements.
- Advertisements and their revisions require Admin approval before publication.
- Only one ACTIVE subscription is allowed per Business Owner.
- Subscription renewal is permitted only when the system opens a renewal window following notification.
- Renewal payments must reference the correct subscription.

Some business rules are enforced by database constraints; workflow rules must also be implemented by the backend.

## 14. Stop Database

```bash
docker compose down
```

This stops and removes the database container while preserving the named volume and stored data.

Do not use `docker compose down -v` unless you intentionally want to delete the local database volume.
