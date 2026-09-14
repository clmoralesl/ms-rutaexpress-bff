# ==========================================
# Etapa 1: Construccion (Build)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copiar configuracion de dependencias para aprovechar la cache de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar codigo fuente y compilar JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Etapa 2: Ejecucion Ligera (Runtime)
# ==========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Crear usuario y grupo sin privilegios para mayor seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar el artefacto compilado desde la etapa de construccion
COPY --from=builder /build/target/*.jar app.jar

# Exponer el puerto del microservicio
EXPOSE 8080

# Parametros de JVM optimizados para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
