FROM eclipse-temurin:8-jre-alpine

WORKDIR /app

COPY recipe-backend-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 9001

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
