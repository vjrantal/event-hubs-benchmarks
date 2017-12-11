using System;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Azure.EventHubs;
using Microsoft.Azure.EventHubs.Processor;

namespace dotnet_event_processor_host
{
  public static class Globals
  {
    public static readonly string guid = Guid.NewGuid().ToString();
    public static void Print(string message)
    {
      Console.WriteLine($"{DateTime.UtcNow}: {message}");
    }
  }

  class Program
  {
    private static readonly AutoResetEvent _closing = new AutoResetEvent(false);

    public static void Main(string[] args)
    {
      MainAsync(args).GetAwaiter().GetResult();
    }

    private static async Task MainAsync(string[] args)
    {
      string EhConnectionString = Environment.GetEnvironmentVariable("EVENT_HUB_CONNECTION_STRING");
      string StorageConnectionString = Environment.GetEnvironmentVariable("STORAGE_CONNECTION_STRING");
      string StorageContainerName = "dotnet-leases";

      Globals.Print($"Registering EventProcessor {Globals.guid}...");

      var eventProcessorHost = new EventProcessorHost(
          null,
          "$Default",
          EhConnectionString,
          StorageConnectionString,
          StorageContainerName);

      // Registers the Event Processor Host and starts receiving messages
      await eventProcessorHost.RegisterEventProcessorAsync<SimpleEventProcessor>();

      Globals.Print("Receiving...");
      Console.CancelKeyPress += new ConsoleCancelEventHandler(OnExit);
      _closing.WaitOne();
    }

    protected static void OnExit(object sender, ConsoleCancelEventArgs args)
    {
      Globals.Print("Exit...");
      _closing.Set();
    }
  }

}
