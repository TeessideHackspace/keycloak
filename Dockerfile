FROM --platform=linux/amd64 maven:3.9.4-eclipse-temurin-17-alpine as build

WORKDIR /build
COPY ./extension .
RUN mvn package

FROM quay.io/keycloak/keycloak:25.0.1
COPY --from=build /build/target/uk.org.teessidehackspace-user-migration.jar /opt/keycloak/providers/user-migration.jar
COPY ./theme /opt/keycloak/themes/teessidehackspace/