# DTRPS Backend – Database Setup

## 1. Overview

DTRPS (Travel Discovery & Planning) uses PostgreSQL with pgvector for storing application data and supporting vector-based place discovery.

The database schema is managed using Flyway migrations.

**Technology Stack**
- Java 21
- Spring Boot 3.5.6
- Maven
- PostgreSQL 17
- pgvector
- Docker Compose
- Flyway

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

## 4. Start PostgreSQL

The repository includes a `compose.yaml` file.

Run:

```bash
docker compose up -d
```

Check container status:

```bash
docker compose ps
```

**Local Database Configuration**

| Property | Value |
|---|---|
| Host | localhost |
| Port | 5433 |
| Database | dtrps |
| Username | dtrps |
| Password | local_dev_only |

The password is intended for local development only.

## 5. Configure Database Password

In PowerShell:

```powershell
$env:DB_PASSWORD = Read-Host "Enter PostgreSQL password"
```

Enter the local development password configured in `compose.yaml`.

The environment variable must be set in the terminal used to run Spring Boot.

## 6. Run Backend and Database Migrations

```powershell
mvn spring-boot:run
```

If port 8080 is occupied:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Flyway automatically applies pending database migrations when the Spring Boot application starts.

Migration files are located at:

```text
src/main/resources/db/migration/
```

Current schema version: **V13**.

## 7. Verify Database

Connect to PostgreSQL:

```bash
docker compose exec db psql -U dtrps -d dtrps
```

Check migration history:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Check database table count:

```sql
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'public'
AND table_type = 'BASE TABLE';
```

The current schema contains 40 tables, including the Flyway history table.

Exit PostgreSQL:

```text
\q
```

## 8. Database Development Rules

1. Do not modify migration files that have already been applied.
2. Create a new Flyway migration when changing the database schema.
3. Use sequential migration versions, starting from V14 for future changes.
4. Commit migration files to Git so all developers use the same database structure.
5. Do not commit production credentials or private environment files.
6. Do not delete the PostgreSQL Docker volume unless intentionally resetting local data.

## 9. Important Business Rules

- A user may have multiple roles, including Traveller and Business Owner.
- Each Business Owner manages at most one business listing.
- Changes to business listing information require Admin approval before publication.
- A business listing can have multiple advertisements.
- Advertisements and their revisions require Admin approval before publication.
- Only one ACTIVE subscription is allowed per Business Owner.
- Subscription renewal is permitted only when the system opens a renewal window following notification.
- Renewal payments must reference the correct subscription.

Some business rules are enforced by database constraints; workflow rules must also be implemented by the backend.

## 10. Stop Database

```bash
docker compose down
```

This stops and removes the database container while preserving the named volume and stored data.

Do not use `docker compose down -v` unless you intentionally want to delete the local database volume.