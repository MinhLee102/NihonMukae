# Build stage
FROM maven:3.9.6-eclipse-temurin-21 as builder
WORKDIR /app

COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./
COPY src ./src
# Package the application
RUN mvn -B -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY --from=builder /app/${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
