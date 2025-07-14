# Use OpenJDK image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src src

# Package the application
RUN ./mvnw package -DskipTests

# Run the application
CMD ["java", "-jar", "target/bizstore-0.0.1-SNAPSHOT.jar"] 