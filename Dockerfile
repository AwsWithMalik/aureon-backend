FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /backend

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /backend

COPY --from=build /backend/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]