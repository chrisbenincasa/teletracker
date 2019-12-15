resource "aws_sqs_queue" "teletracker-task-queue" {
  name = "teletracker-tasks-qa"
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