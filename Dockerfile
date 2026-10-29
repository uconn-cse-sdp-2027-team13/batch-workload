FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src/Main.java .
RUN javac -d out Main.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./
ENTRYPOINT ["java", "-cp", "/app", "Main"]
