resource "aws_sqs_queue" "consumer_queue" {
  name                        = "${var.queue_name}${var.fifo ? ".fifo" : ""}"
  fifo_queue                  = var.fifo
  content_based_deduplication = var.content_based_dedupe
  redrive_policy              = "{\"deadLetterTargetArn\":\"${aws_sqs_queue.consumer_dlq.arn}\",\"maxReceiveCount\":3}"
}

resource "aws_sqs_queue" "consumer_dlq" {
  name                        = "${var.queue_name}-dlq${var.fifo ? ".fifo" : ""}"
  fifo_queue                  = var.fifo
  content_based_deduplication = false
}