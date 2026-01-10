FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :product-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/product-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
