# Introduction

This repository contains samples using the so-called [Event Processor Host](https://blogs.msdn.microsoft.com/servicebus/2015/01/16/event-processor-host-best-practices-part-1/) (EPH) in three different programming. All implement checkpointing and telemetry logging to [Application Insights](https://azure.microsoft.com/en-us/services/application-insights/) in a similar way.

All three subfolders (named after the programming language used) also contain a `Dockerfile` and `deployment.yaml` so that the EPHs can be easily deployed on Kubernetes.

The `README.md` files in the subfolders contain the steps required to run locally. Before running, you need to create the required Azure resources and set the right environment variables (see below).

# Required Azure resources

You need an Azure Storage Account with Blob container in it that stores the blobs used for lease data.

Event Hubs namespace with at least one Event Hub in it. The environment variable used below is expected to be specific to the Event Hub.

Application Insights is optional and if not available, the throughput still gets logged as output from the app.

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

# Application Insights queries

Metrics per EPH instance:

```
customMetrics
| where timestamp > datetime("2017-11-20T08:53:43")
| summarize sum_by_name=sum(value) by name, bin(timestamp, 10s)
| summarize avg(sum_by_name) by name, bin(timestamp, 30s)
| render timechart
```

Aggregate across all EPH instances:

```
customMetrics
| where timestamp > datetime("2017-11-20T08:53:43")
| summarize sum_all=sum(value) by operation_SyntheticSource, bin(timestamp, 10s)
| summarize avg(sum_all) by operation_SyntheticSource, bin(timestamp, 30s)
| render timechart
```