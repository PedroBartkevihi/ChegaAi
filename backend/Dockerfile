# Imagem da API (contexto de build: a pasta backend)
#   docker build -t chegaai-api backend
# As imagens base ficam fixadas por digest (a tag é só para leitura); o Dependabot propõe as atualizações.

# --- Etapa 1: compila o jar ---
FROM maven:3.9-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package

# --- Etapa 2: só o JRE e o jar (as migrations do Flyway vão dentro dele) ---
FROM eclipse-temurin:17-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0
WORKDIR /app
RUN addgroup -S chegaai && adduser -S chegaai -G chegaai
COPY --from=build /app/target/chegaai-backend-*.jar app.jar
USER chegaai
EXPOSE 8080
# Pouca memória nos planos gratuitos: heap limitado à memória do contêiner,
# coletor serial (o mais leve) e compilação JIT mais simples para iniciar mais rápido.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s \
  CMD wget -qO- http://localhost:${PORT:-8080}/api/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
