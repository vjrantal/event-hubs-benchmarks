# Running locally

Install Java and Apache Maven.

Run:

```
mvn clean package
java -jar target/event-hubs-benchmarks-java-1.0.0-jar-with-dependencies.jar
```

# Running within docker

To build:

```
docker build . -t vjrantal/eph-java:latest
```

To run:

```
docker run -it -e STORAGE_CONNECTION_STRING=$STORAGE_CONNECTION_STRING -e EVENT_HUB_CONNECTION_STRING=$EVENT_HUB_CONNECTION_STRING -e INSTRUMENTATION_KEY=$INSTRUMENTATION_KEY vjrantal/eph-java:latest
```

# Deploying to Kubernetes

Modify `deployment.yaml` to have the correct environment variables and then deploy:

```
kubectl apply -f deployment.yaml
```

Alternatively, if you prefer to not modify `deployment.yaml` and have `envsubst` from `gettext` installed, you can get the environment variables dynamically injected from your host environment with:

```
envsubst < deployment.yaml | kubectl apply -f -
```
