package com.teletracker.common.pubsub

trait SettableReceiptHandle {
  def receiptHandle: Option[String]
  def setReceiptHandle(handle: Option[String]): Unit
}
