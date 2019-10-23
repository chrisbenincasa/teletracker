//package com.teletracker.service.logging;
//
//import com.google.cloud.logging.LogEntry
//import com.google.cloud.logging.LoggingEnhancer
//
//class TeletrackerLogsEnhancer extends LoggingEnhancer {
//  final private val application =
//    Option(System.getenv("APPLICATION_NAME")).getOrElse("teletracker-unknown")
//
//  def enhanceLogEntry(logEntry: LogEntry.Builder): Unit = {
//    logEntry.addLabel("application", application);
//  }
//}
