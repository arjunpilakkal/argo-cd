# Use a lightweight OpenJDK image
FROM eclipse-temurin:17-jre-alpine

# Set a working directory inside the container
WORKDIR /app

# Copy the built JAR from the workspace to the container
COPY target/sample-app-1.0-SNAPSHOT.jar app.jar

# Expose the port your app will run on
EXPOSE 8080

# Command to run the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]
