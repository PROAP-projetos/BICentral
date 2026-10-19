# --- build ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# --- run ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Os gráficos dos relatórios (JFreeChart, ver GraficosRelatorio) desenham texto com o Java2D, que
# precisa de fontes instaladas — sem elas a geração do PDF/DOCX falha em produção mesmo passando local.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar

# Sem isso a JVM roda em UTC (padrão da imagem base) — LocalDateTime.now()/LocalDate.now() sem
# timezone explícita no código (relatório, expiração de convite/senha, prazo de tarefas) ficavam
# 3h adiantados em produção.
ENV TZ=America/Sao_Paulo

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
