FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle /workspace/
COPY gradle /workspace/gradle
COPY src /workspace/src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
