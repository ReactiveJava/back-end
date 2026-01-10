FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :admin-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/admin-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
