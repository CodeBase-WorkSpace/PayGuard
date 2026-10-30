# Build the reactor outside this image; credentials never enter the build context.
FROM hashicorp/vault:2.1.2@sha256:c2f666266f383d2cf424d86b8bb8ce7d065562173ffec2b476d762943608bb55 AS vault
FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
COPY --from=vault /bin/vault /usr/local/bin/vault
ARG SERVICE
WORKDIR /app
COPY payguard-${SERVICE}-service/target/payguard-${SERVICE}-service-1.0.0-SNAPSHOT.jar /app/service.jar
USER 1000:1000
ENTRYPOINT ["vault", "agent"]
