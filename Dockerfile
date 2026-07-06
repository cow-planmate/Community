FROM eclipse-temurin:17-jdk-jammy

ARG JAR_FILE=build/libs/Community-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} /app.jar

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-Duser.timezone=Asia/Seoul", "-jar", "/app.jar"]
