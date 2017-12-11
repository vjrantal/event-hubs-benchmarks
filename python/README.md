# Environment variables

Set the following mandatory environment variables:

```
export STORAGE_CONNECTION_STRING=<storage-connection-used-for-lease-blobs>
export EVENT_HUB_CONNECTION_STRING=<event-hub-to-connect-to-that-includes-entitypath>
```

If you want optional Application Insights logging, set the instrumentation key:

```
export INSTRUMENTATION_KEY=<application-insights-instrumentation-key>
```

# Running locally

Install requirements:

```
pip3 install python-qpid-proton
pip3 install -r requirements.txt
```

Run:

```
python3 benchmark.py
```

# Running within docker

To build:

```
docker build . -t vjrantal/eph-python:latest
```

To run:

```
docker run -it -e STORAGE_CONNECTION_STRING=$STORAGE_CONNECTION_STRING -e EVENT_HUB_CONNECTION_STRING=$EVENT_HUB_CONNECTION_STRING -e INSTRUMENTATION_KEY=$INSTRUMENTATION_KEY vjrantal/eph-python:latest
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
