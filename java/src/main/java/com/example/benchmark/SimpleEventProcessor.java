package com.example.benchmark;

/*
 * Until the official release, there is no package distributed for EventProcessorHost, and hence no good
 * portable way of putting a reference to it in the samples POM. Thus, the contents of this sample are
 * commented out by default to avoid blocking or breaking anything. To use this sample, add a dependency
 * on EventProcessorHost, then uncomment.
 */

import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventprocessorhost.CloseReason;
import com.microsoft.azure.eventprocessorhost.EventProcessorHost;
import com.microsoft.azure.eventprocessorhost.EventProcessorOptions;
import com.microsoft.azure.eventprocessorhost.ExceptionReceivedEventArgs;
import com.microsoft.azure.eventprocessorhost.IEventProcessor;
import com.microsoft.azure.eventprocessorhost.PartitionContext;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SimpleEventProcessor {
  public static void main(String args[]) {
    Map<String, String> env = System.getenv();
    String consumerGroupName = "$Default";
    
    String eventHubConnectionString = env.get("EVENT_HUB_CONNECTION_STRING");
    String storageConnectionString = env.get("STORAGE_CONNECTION_STRING");
    String storageContainerName = "java-leases";

    // Create the instance of EventProcessorHost using the most basic constructor. This constructor uses Azure Storage for
    // persisting partition leases and checkpoints, with a default Storage container name made from the Event Hub name
    // and consumer group name. The host name (a string that uniquely identifies the instance of EventProcessorHost)
    // is automatically generated as well.
    EventProcessorHost host = new EventProcessorHost(UUID.randomUUID().toString(), null, consumerGroupName,
        eventHubConnectionString.toString(), storageConnectionString, storageContainerName);

    // Registering an event processor class with an instance of EventProcessorHost starts event processing. The host instance
    // obtains leases on some partitions of the Event Hub, possibly stealing some from other host instances, in a way that
    // converges on an even distribution of partitions across all host instances. For each leased partition, the host instance
    // creates an instance of the provided event processor class, then receives events from that partition and passes them to
    // the event processor instance.
    //
    // There are two error notification systems in EventProcessorHost. Notification of errors tied to a particular partition,
    // such as a receiver failing, are delivered to the event processor instance for that partition via the onError method.
    // Notification of errors not tied to a particular partition, such as initialization failures, are delivered to a general
    // notification handler that is specified via an EventProcessorOptions object. You are not required to provide such a
    // notification handler, but if you don't, then you may not know that certain errors have occurred.
    System.out.println("Registering host named " + host.getHostName());
    EventProcessorOptions options = new EventProcessorOptions();
    options.setExceptionNotification(new ErrorNotificationHandler());
    try {
      // The Future returned by the register* APIs completes when initialization is done and
      // message pumping is about to start. It is important to call get() on the Future because
      // initialization failures will result in an ExecutionException, with the failure as the
      // inner exception, and are not otherwise reported.
      host.registerEventProcessor(EventProcessor.class, options).get();
    } catch (Exception e) {
      System.out.print("Failure while registering: ");
      if (e instanceof ExecutionException) {
        Throwable inner = e.getCause();
        System.out.println(inner.toString());
      } else {
        System.out.println(e.toString());
      }
    }
  }

  // The general notification handler is an object that derives from Consumer<> and takes an ExceptionReceivedEventArgs object
  // as an argument. The argument provides the details of the error: the exception that occurred and the action (what EventProcessorHost
  // was doing) during which the error occurred. The complete list of actions can be found in EventProcessorHostActionStrings.
  public static class ErrorNotificationHandler implements Consumer<ExceptionReceivedEventArgs> {
    @Override
    public void accept(ExceptionReceivedEventArgs t) {
      System.out.println("Host " + t.getHostname() + " received general error notification during "
          + t.getAction() + ": " + t.getException().toString());
    }
  }

  public static class EventProcessor implements IEventProcessor {
    private int messageCount = 0;
    private int checkpointInterval = 10;
    private long previousCheckpoint = System.currentTimeMillis();

    private String hostName = "";
    private TelemetryClient telemetryClient = null;

    // OnOpen is called when a new event processor instance is created by the host. In a real implementation, this
    // is the place to do initialization so that events can be processed when they arrive, such as opening a database
    // connection.
    @Override
    public void onOpen(PartitionContext context) throws Exception {
      this.hostName = InetAddress.getLocalHost().getHostName();
      Map<String, String> env = System.getenv();
      String instrumentationKey = env.get("INSTRUMENTATION_KEY");
      if (instrumentationKey != null) {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.createDefault();
        telemetryConfiguration.setInstrumentationKey(instrumentationKey);
        this.telemetryClient = new TelemetryClient(telemetryConfiguration);
      }
      System.out.println("Partition " + context.getPartitionId() + " is opening");
    }

    // OnClose is called when an event processor instance is being shut down. The reason argument indicates whether the shut down
    // is because another host has stolen the lease for this partition or due to error or host shutdown. In a real implementation,
    // this is the place to do cleanup for resources that were opened in onOpen.
    @Override
    public void onClose(PartitionContext context, CloseReason reason) throws Exception {
      System.out
          .println("Partition " + context.getPartitionId() + " is closing for reason " + reason.toString());
    }

    // onError is called when an error occurs in EventProcessorHost code that is tied to this partition, such as a receiver failure.
    // It is NOT called for exceptions thrown out of onOpen/onClose/onEvents. EventProcessorHost is responsible for recovering from
    // the error, if possible, or shutting the event processor down if not, in which case there will be a call to onClose. The
    // notification provided to onError is primarily informational.
    @Override
    public void onError(PartitionContext context, Throwable error) {
      System.out.println("Partition " + context.getPartitionId() + " onError: " + error.toString());
    }

    // onEvents is called when events are received on this partition of the Event Hub. The maximum number of events in a batch
    // can be controlled via EventProcessorOptions. Also, if the "invoke processor after receive timeout" option is set to true,
    // this method will be called with null when a receive timeout occurs.
    @Override
    public void onEvents(PartitionContext context, Iterable<EventData> messages) throws Exception {
      EventData latestEventData = null;
      for (EventData data : messages) {
        //System.out.println("(" + context.getPartitionId() + "," + data.getSystemProperties().getOffset() + ","
        //    + data.getSystemProperties().getSequenceNumber() + "): " + new String(data.getBytes(), "UTF8"));
        this.messageCount++;
        latestEventData = data;
      }
      // Checkpointing persists the current position in the event stream for this partition and means that the next
      // time any host opens an event processor on this event hub+consumer group+partition combination, it will start
      // receiving at the event after this one. Checkpointing is usually not a fast operation, so there is a tradeoff
      // between checkpointing frequently (to minimize the number of events that will be reprocessed after a crash, or
      // if the partition lease is stolen) and checkpointing infrequently (to reduce the impact on event processing
      // performance). Checkpointing every five events is an arbitrary choice for this sample.
      long timeNow = System.currentTimeMillis();
      long delta = timeNow - this.previousCheckpoint;
      if (delta > this.checkpointInterval * 1000) {
        int messagesPerSecond = (int)(this.messageCount / (delta / 1000));
        System.out.println("Starting to checkpoint - current speed on partition " + context.getPartitionId() + ": " + messagesPerSecond + " events / s");
        this.messageCount = 0;
        this.previousCheckpoint = System.currentTimeMillis();

        if (this.telemetryClient != null) {
          Map<String, String> properties = new HashMap<String, String>();
          properties.put("p", context.getPartitionId());
          this.telemetryClient.trackMetric(this.hostName, messagesPerSecond, 1, messagesPerSecond, messagesPerSecond, properties);
        }

        context.checkpoint(latestEventData);
      }
    }
  }
}
