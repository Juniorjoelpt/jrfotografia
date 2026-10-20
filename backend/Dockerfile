FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 jr && mkdir -p /dados/arquivos && chown -R jr /dados
COPY --from=build /app/target/jrfotografia-api-*.jar app.jar
USER jr
ENV ARQUIVOS_PASTA=/dados/arquivos
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
