FROM maven:3.5.2-jdk-8

COPY . .
RUN mvn clean package

CMD java -jar target/event-hubs-benchmarks-java-1.0.0-jar-with-dependencies.jar
