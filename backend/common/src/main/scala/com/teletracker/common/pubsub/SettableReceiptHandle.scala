package com.teletracker.common.pubsub

trait SettableReceiptHandle {
  def receiptHandle: Option[String]
  def setReceiptHandle(handle: Option[String]): Unit
}

trait SettableGroupId {
  def messageGroupId: Option[String]
  def setMessageGroupId(id: Option[String]): Unit
}
