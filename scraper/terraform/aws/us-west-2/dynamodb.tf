resource "aws_dynamodb_table" "crawls" {
  name      = "teletracker.qa.crawls"
  hash_key  = "spider"
  range_key = "version"

  attribute {
    name = "spider"
    type = "S"
  }

  attribute {
    name = "version"
    type = "N"
  }

  billing_mode = "PAY_PER_REQUEST"
}