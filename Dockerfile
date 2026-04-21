# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src

COPY gradlew gradlew
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY common ./common
COPY service ./service
RUN chmod +x gradlew \
 && ./gradlew :service:${MODULE}:bootJar --no-daemon -x test \
 && cp service/${MODULE}/build/libs/*.jar /tmp/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jdk AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd  --system --gid app --home /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","/app/app.jar"]
