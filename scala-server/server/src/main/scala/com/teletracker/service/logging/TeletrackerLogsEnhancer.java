package com.teletracker.service.logging;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingEnhancer;

class TeletrackerLogsEnhancer implements LoggingEnhancer {
  public void enhanceLogEntry(LogEntry.Builder logEntry) {
    logEntry.addLabel("application", "teletracker-server");
  }
}