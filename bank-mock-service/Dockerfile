FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :bank-mock-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bank-mock-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
