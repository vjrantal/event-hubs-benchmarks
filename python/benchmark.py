"""
EventProcessorHost used to benchmark how many messages per second can be handled.
"""

import logging
import os
import sys
import asyncio
import time
import urllib
import platform
from eventprocessorhost.abstract_event_processor import AbstractEventProcessor
from eventprocessorhost.azure_storage_checkpoint_manager import AzureStorageCheckpointLeaseManager
from eventprocessorhost.eh_config import EventHubConfig
from eventprocessorhost.eph import EventProcessorHost
import applicationinsights

class EventProcessor(AbstractEventProcessor):
    """
    EventProcessor that logs metrics to Application Insights
    """
    def __init__(self, params):
        """
        Init Event processor
        """
        super().__init__()
        self.telemetry_client = params[0]
        self.checkpoint_interval = 10
        self.previous_checkpoint = time.time()
        self.counter = 0

    async def open_async(self, context):
        """
        Called by processor host to initialize the event processor.
        """
        logging.info("Connection established %s", context.partition_id)

    async def close_async(self, context, reason):
        """
        Called by processor host to indicate that the event processor is being stopped.
        (Params) Context:Information about the partition
        """
        logging.info("Connection closed (reason %s, id %s, offset %s, sq_number %s)", reason,
                     context.partition_id, context.offset, context.sequence_number)

    async def checkpoint_async(self, context):
        """
        Tracks the time from when the previous checkpoint occurred and if it was longer than
        checkpoint_interval ago, will trigger checkpointing via the context object.
        """
        delta = time.time() - self.previous_checkpoint
        if delta > self.checkpoint_interval:
            events_per_second = int(self.counter / delta)
            logging.info("Starting to checkpoint - current speed on partition %s: %s events / s",
                         context.partition_id, events_per_second)
            self.counter = 0
            self.previous_checkpoint = time.time()
            if self.telemetry_client:
                self.telemetry_client.track_metric(platform.node(), events_per_second,
                                                   properties={'p': context.partition_id})
                self.telemetry_client.flush()
            await context.checkpoint_async()

    async def process_events_async(self, context, messages):
        """
        Called by the processor host when a batch of events has arrived.
        This is where the real work of the event processor is done.
        (Params) Context: Information about the partition, Messages: The events to be processed.
        """
        #logging.info("%s events processed from partition %s", len(messages), context.partition_id)
        message_count = len(messages)
        self.counter += message_count
        await self.checkpoint_async(context)

    async def process_error_async(self, context, error):
        """
        Called when the underlying client experiences an error while receiving.
        EventProcessorHost will take care of recovering from the error and
        continuing to pump messages,so no action is required from
        (Params) Context: Information about the partition, Error: The error that occured.
        """
        logging.error("Event Processor Error %s ", repr(error))

# Configure Logging
LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)
FORMATTER = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
#FORMATTER = logging.Formatter("%(asctime)s - %(levelname)s - " +
#                              "%(pathname)s:%(lineno)s - %(message)s")
STREAM_HANDLER = logging.StreamHandler(stream=sys.stdout)
STREAM_HANDLER.setFormatter(FORMATTER)
LOGGER.addHandler(STREAM_HANDLER)

TELEMETRY_CLIENT = None
INSTRUMENTATION_KEY = os.environ.get("INSTRUMENTATION_KEY")
if INSTRUMENTATION_KEY:
    TELEMETRY_CHANNEL = applicationinsights.channel.TelemetryChannel(
        None,
        applicationinsights.channel.AsynchronousQueue(
            applicationinsights.channel.AsynchronousSender())
    )
    TELEMETRY_CLIENT = applicationinsights.TelemetryClient(INSTRUMENTATION_KEY, TELEMETRY_CHANNEL)

STORAGE_CONNECTION_STRING = os.environ.get("STORAGE_CONNECTION_STRING")
if not STORAGE_CONNECTION_STRING:
    raise Exception("Please set environment variable STORAGE_CONNECTION_STRING")
STORAGE = dict(token.split("=", 1) for token in STORAGE_CONNECTION_STRING.split(";"))

# Storage Account Credentials
STORAGE_ACCOUNT_NAME = STORAGE["AccountName"]
STORAGE_KEY = STORAGE["AccountKey"]
LEASE_CONTAINER_NAME = "python-leases"

EVENT_HUB_CONNECTION_STRING = os.environ.get("EVENT_HUB_CONNECTION_STRING")
if not EVENT_HUB_CONNECTION_STRING:
    raise Exception("Please set environment variable EVENT_HUB_CONNECTION_STRING")
EVENT_HUB = dict(token.split("=", 1) for token in EVENT_HUB_CONNECTION_STRING.split(";"))
NAMESPACE = urllib.parse.urlparse(EVENT_HUB["Endpoint"]).netloc.split('.')[0]
ENTITY = EVENT_HUB["EntityPath"]
CONSUMER_GROUP = "$Default"
POLICY_NAME = EVENT_HUB["SharedAccessKeyName"]
POLICY_KEY = EVENT_HUB["SharedAccessKey"]

EH_CONFIG = EventHubConfig(NAMESPACE, ENTITY, POLICY_NAME,
                           POLICY_KEY,
                           CONSUMER_GROUP)
STORAGE_MANAGER = AzureStorageCheckpointLeaseManager(STORAGE_ACCOUNT_NAME,
                                                     STORAGE_KEY,
                                                     LEASE_CONTAINER_NAME)

LOOP = asyncio.get_event_loop()
HOST = EventProcessorHost(EventProcessor, EH_CONFIG, STORAGE_MANAGER,
                          ep_params=[TELEMETRY_CLIENT], loop=LOOP)
LOOP.run_until_complete(HOST.open_async())
LOOP.run_until_complete(HOST.close_async())
