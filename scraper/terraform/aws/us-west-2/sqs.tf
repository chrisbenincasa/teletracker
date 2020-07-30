data "aws_sqs_queue" "scrape_item_output_queue" {
  name = "scrape-item-output-queue-qa"
}

data "aws_sqs_queue" "amazon_item_output_queue" {
  name = "amazon-scrape-item-queue-qa"
}