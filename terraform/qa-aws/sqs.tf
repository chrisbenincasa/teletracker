resource "aws_sqs_queue" "teletracker-task-queue" {
  name                        = "teletracker-tasks-qa.fifo"
  fifo_queue                  = true
  content_based_deduplication = true
  redrive_policy              = "{\"deadLetterTargetArn\":\"${aws_sqs_queue.teletracker-task-dlq.arn}\",\"maxReceiveCount\":3}"
}

resource "aws_sqs_queue" "teletracker-task-dlq" {
  name                        = "teletracker-tasks-dlq-qa.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
}

resource "aws_sqs_queue" "teletracker-es-ingest-queue" {
  name                        = "teletracker-es-ingest-qa.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
  redrive_policy              = "{\"deadLetterTargetArn\":\"${aws_sqs_queue.teletracker-es-ingest-dlq.arn}\",\"maxReceiveCount\":3}"
}

resource "aws_sqs_queue" "teletracker-es-ingest-dlq" {
  name                        = "teletracker-es-ingest-failed.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
}

resource "aws_sqs_queue_policy" "teletracker-task-queue-policy" {
  queue_url = aws_sqs_queue.teletracker-task-queue.id

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Id": "sqspolicy",
  "Statement": [{
      "Effect": "Allow",
      "Principal": {
          "Service": [
            "events.amazonaws.com",
            "sqs.amazonaws.com"
          ]
      },
      "Action": "sqs:SendMessage",
      "Resource": "${aws_sqs_queue.teletracker-task-queue.arn}"
  }]
}
POLICY
}