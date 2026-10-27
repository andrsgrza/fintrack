# Analytics R3-0 baseline

## Isolation

This worktree is the clean Analytics refactor baseline. It is intentionally
independent from `implement-analytics` and must not use the normal FINTRACK
development database.

- Branch: `refactor-analytics`
- Base: `e49d76ddcf529d18961012b54514f6de7ce74887`
- Worktree: `../fintrack-analytics-refactor`
- Untouched reference checkout: `../fintrack` on `implement-analytics`
- PostgreSQL host port: `55432`
- Database: `fintrack_analytics_refactor`
- User: `fintrack_analytics_refactor`
- Application port: `19080`

Start the disposable database only from this worktree:

```bash
docker compose -f src/main/docker/analytics-refactor-postgresql.yml up --wait
```

The local `dev` and `prod` profiles, as well as the Maven Liquibase profile,
default to this datasource. Environment variables beginning with
`FINTRACK_ANALYTICS_REFACTOR_DB_` may override it, but must continue to
identify an isolated database. Testcontainers tests remain isolated by their
own ephemeral containers.

Stop and remove only this refactor's disposable resource after local testing:

```bash
docker compose -f src/main/docker/analytics-refactor-postgresql.yml down -v
```

Never run Liquibase, `docker compose down -v`, or an application from this
worktree against `localhost:5432/fintrack`.

## Verification commands

With the disposable database running, use its dedicated password only for
refactor commands:

```bash
FINTRACK_ANALYTICS_REFACTOR_DB_PASSWORD=fintrack_analytics_refactor_dev_only \
  ./mvnw process-resources liquibase:update -Pdev
npm run lint
npm run jest
npm run webapp:prod
npm run backend:unit:test
./mvnw --batch-mode package -DskipTests -Pprod
SPRING_PROFILES_ACTIVE=dev \
  FINTRACK_ANALYTICS_REFACTOR_DB_PASSWORD=fintrack_analytics_refactor_dev_only \
  java -jar target/fintrack-0.0.1-SNAPSHOT.jar
```

## Preserved baseline

The base retains FinancialAccount, FinancialTransaction, Category, Tag,
InternalTransfer, TransactionBatch, authentication, ownership enforcement and
their supporting Liquibase history.

## Retired obsolete infrastructure

`UserDashboardPreference` was an obsolete pre-Analytics dashboard preference.
R3-0 removes its generated Java runtime, REST route, repository, frontend
route/reducer/screens, translations, JHipster metadata, JDL declaration,
tests and Liquibase master includes. Its historical changelog files are absent
from this clean branch, so a fresh refactor database does not create that
table.

Older product-history documents may mention the retired entity; they are not
runtime contracts for this refactor baseline.

## Selective future reuse

Do not bulk-copy Analytics from `implement-analytics`. R3-1 may selectively
port verified financial policies, calculation primitives and tests only after
they are expressed through the new canonical contracts. No Analytics V2
contracts are implemented by R3-0. The exact read-only inventory and its
`REUSE_LOGIC` / `REWRITE_FOR_NEW_CONTRACT` / `DO_NOT_PORT` decisions are in
[`ANALYTICS-R3-0-REUSE-INVENTORY.md`](ANALYTICS-R3-0-REUSE-INVENTORY.md).
