FROM amazoncorretto:17-alpine-jdk

RUN mkdir /app

ARG JAR_PATH=target/*.jar
COPY ${JAR_PATH} /app/app.jar

ENTRYPOINT java -jar /app/app.jar