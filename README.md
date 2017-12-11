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
