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

# 1GB 프리티어 대응 JVM 튜닝: 1vCPU엔 SerialGC가 메모리 오버헤드 최소,
# 힙·metaspace·코드캐시 상한 + 작은 스레드 스택으로 RSS 예측 가능하게,
# OOM 시 스래싱 대신 깨끗이 종료(→ docker restart 정책으로 재기동)
ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-Xss512k", \
  "-Xmx256m", \
  "-XX:MaxMetaspaceSize=256m", \
  "-XX:ReservedCodeCacheSize=64m", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "/app/app.jar"]
