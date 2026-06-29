# Build :backend only (Android modules excluded via -PbackendOnly → no Android SDK needed).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN sh ./gradlew :backend:installDist -PbackendOnly --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ ./
EXPOSE 8080
CMD ["bin/backend"]
