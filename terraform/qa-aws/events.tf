// Allow cloudwatch events to fire SQS messages
resource "aws_iam_role" "cloudwatch_event_iam_role" {
  name               = "cloudwatch-events-role"
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": ["events.amazonaws.com", "sqs.amazonaws.com"]
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "cloudwatch_event_iam_sqs_full_access_attachment" {
  role       = aws_iam_role.cloudwatch_event_iam_role.name
  policy_arn = data.aws_iam_policy.sqs_full_access_policy.arn
}

data "aws_iam_policy" "sqs_full_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSQSFullAccess"
}
