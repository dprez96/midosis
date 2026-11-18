# Imagen de core para Cloud Run. Se arma con el jar ya compilado (./gradlew bootJar), porque
# core depende de motor y contrato por composite build y no se compila solo dentro de la imagen.
# desplegar.sh copia el jar como app.jar junto a este archivo.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S midosis && adduser -S midosis -G midosis
WORKDIR /app
COPY app.jar app.jar
USER midosis

# Cloud Run entrega el puerto en PORT (8080 por defecto); Spring Boot escucha en 8080.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
