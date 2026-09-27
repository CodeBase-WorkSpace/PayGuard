# Collateral service

The service owns cash collateral locks, valuation history, LTV thresholds, margin calls, liquidation authorization, retention metadata, audit records, and outbox publication. Price and liquidation integrations are ports; the portfolio profile supplies a simulated foreign-currency oracle and execution adapter.

Build independently from the repository root with `docker build -f payguard-collateral-service/Dockerfile -t payguard/collateral-service .`. Run it with `SPRING_DATASOURCE_URL="$COLLATERAL_ORACLE_JDBC_URL"`, `SPRING_DATASOURCE_USERNAME="$COLLATERAL_ORACLE_APP_USER"`, and `SPRING_DATASOURCE_PASSWORD="$COLLATERAL_ORACLE_APP_PASSWORD"` from `.env`; its Flyway schema is applied only to the collateral-owned Oracle instance.
