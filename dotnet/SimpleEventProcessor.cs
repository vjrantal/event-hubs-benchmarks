using System;
using System.Collections.Generic;
using System.Text;
using System.Diagnostics;
using System.Threading.Tasks;
using Microsoft.Azure.EventHubs;
using Microsoft.Azure.EventHubs.Processor;
using Microsoft.ApplicationInsights;
using Microsoft.ApplicationInsights.DataContracts;

namespace dotnet_event_processor_host
{
  public class SimpleEventProcessor : IEventProcessor
  {
    private TelemetryClient telemetryClient = null;
    private string hostname = System.Environment.MachineName;
    private Stopwatch _checkpointStopWatch;
    private int counter = 0;

    public SimpleEventProcessor()
    {
      string instrumentationKey = Environment.GetEnvironmentVariable("INSTRUMENTATION_KEY");
      if (instrumentationKey != null) {
        telemetryClient = new TelemetryClient();
        telemetryClient.InstrumentationKey = instrumentationKey;
      }
    }

    public async Task CloseAsync(PartitionContext context, CloseReason reason)
    {
      Globals.Print($"Processor Shutting Down. Partition '{context.PartitionId}', Reason: '{reason}'.");
      if (reason == CloseReason.Shutdown)
      {
        await context.CheckpointAsync();
      }
    }

    public Task OpenAsync(PartitionContext context)
    {
      Globals.Print($"SimpleEventProcessor initialized. Partition: '{context.PartitionId}'");
      _checkpointStopWatch = new Stopwatch();
      _checkpointStopWatch.Start();
      return Task.CompletedTask;
    }

    public Task ProcessErrorAsync(PartitionContext context, Exception error)
    {
      Globals.Print($"Error on Partition: {context.PartitionId}, Error: {error.Message}");
      return Task.CompletedTask;
    }

    public async Task ProcessEventsAsync(PartitionContext context, IEnumerable<EventData> messages)
    {
      int eventCount = 0;
      foreach (var eventData in messages)
      {
        eventCount += 1;
        //var data = Encoding.UTF8.GetString(eventData.Body.Array, eventData.Body.Offset, eventData.Body.Count);
        //Globals.Print($"Message received. Partition: '{context.PartitionId}', Data: '{data}'");
      }
      counter += eventCount;

      TimeSpan elapsed = _checkpointStopWatch.Elapsed;
      if (elapsed > TimeSpan.FromSeconds(10))
      {
        int eventsPerSecond = Convert.ToInt32(counter / (elapsed.TotalMilliseconds / 1000));
        Globals.Print($"Starting to checkpoint - current speed on partition {context.PartitionId}: {eventsPerSecond} events / second");

        counter = 0;
        _checkpointStopWatch.Restart();

        if (telemetryClient != null) {
          var metric = new MetricTelemetry(hostname, eventsPerSecond);
          metric.Properties.Add("p", context.PartitionId);
          telemetryClient.TrackMetric(metric);
        }

        await context.CheckpointAsync();
      }
    }
  }
}