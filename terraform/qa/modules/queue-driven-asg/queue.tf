//resource "aws_sqs_queue" "consumer_queue" {
//  name                        = "${var.queue_name}${var.fifo ? ".fifo" : ""}"
//  fifo_queue                  = var.fifo
//  content_based_deduplication = var.fifo ? var.content_based_dedupe : null
//  redrive_policy              = "{\"deadLetterTargetArn\":\"${aws_sqs_queue.consumer_dlq.arn}\",\"maxReceiveCount\":3}"
//}
//
//resource "aws_sqs_queue" "consumer_dlq" {
//  name                        = "${var.queue_name}-dlq${var.fifo ? ".fifo" : ""}"
//  fifo_queue                  = var.fifo
//  content_based_deduplication = var.fifo ? false : null
//}
//
//resource "aws_sqs_queue_policy" "teletracker-task-queue-policy" {
//  queue_url = aws_sqs_queue.consumer_queue.id
//
//  policy = <<POLICY
//{
//  "Version": "2012-10-17",
//  "Id": "sqspolicy",
//  "Statement": [{
//      "Effect": "Allow",
//      "Principal": {
//          "Service": [
//            "events.amazonaws.com",
//            "sqs.amazonaws.com"
//          ]
//      },
//      "Action": "sqs:SendMessage",
//      "Resource": "${aws_sqs_queue.consumer_queue.arn}"
//  }]
//}
//POLICY
//}
