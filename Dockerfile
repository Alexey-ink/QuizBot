FROM eclipse-temurin:23-jdk AS build
WORKDIR /workspace
COPY . /workspace
RUN ./gradlew clean shadowJar -x test

FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

COPY .env /app/.env

CMD ["java", "-jar", "/app/app.jar"]