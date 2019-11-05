resource "aws_sqs_queue" "teletracker-task-queue" {
  name = "teletracker-tasks-qa"
}

data "aws_sqs_queue" "teletracker-task-queue-data" {
  name       = "teletracker-tasks-qa"
  depends_on = [aws_sqs_queue.teletracker-task-queue]
}
