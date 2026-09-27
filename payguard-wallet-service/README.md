# Wallet service

Build independently from the repository root with `docker build -f payguard-wallet-service/Dockerfile -t payguard/wallet-service .`. Run with `docker run --env-file .env -e SPRING_DATASOURCE_URL="$WALLET_ORACLE_JDBC_URL" -e SPRING_DATASOURCE_USERNAME="$WALLET_ORACLE_APP_USER" -e SPRING_DATASOURCE_PASSWORD="$WALLET_ORACLE_APP_PASSWORD" -p 8081:8080 payguard/wallet-service`.
