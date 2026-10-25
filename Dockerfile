FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --no-create-home app

COPY build/libs/admin-api-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]