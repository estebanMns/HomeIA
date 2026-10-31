# Backend de HomeIA (Spring Boot). Lo usa Railway; ver docs/DESPLIEGUE.md.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# En Windows git puede dejar mvnw con fin de línea CRLF, que sh no ejecuta.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 homeia
COPY --from=build /app/target/*.jar app.jar
USER homeia
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Railway define PORT; el backend lo lee con server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
