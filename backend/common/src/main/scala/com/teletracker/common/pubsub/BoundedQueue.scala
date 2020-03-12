package com.teletracker.common.pubsub

trait BoundedQueue[UpperBound <: SettableReceiptHandle]
    extends BoundedQueueWriter[UpperBound]
    with BoundedQueueReader[UpperBound]
