resource "aws_dynamodb_table" "lists-ddb-table" {
  name         = "teletracker.qa.lists"
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "id"
  range_key = "userId"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "legacyId"
    type = "N"
  }

  global_secondary_index {
    name            = "legacyId-userId-index-copy"
    hash_key        = "legacyId"
    range_key       = "userId"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "userId-id-inverted-index"
    hash_key        = "userId"
    range_key       = "id"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "lists-simple-ddb-table" {
  name         = "teletracker.qa.lists-simple"
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "legacyId"
    type = "N"
  }

  global_secondary_index {
    name            = "legacyId-userId-index-copy"
    hash_key        = "legacyId"
    range_key       = "userId"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "userId-id-inverted-index"
    hash_key        = "userId"
    range_key       = "id"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "list-aliases-ddb-table" {
  name         = "teletracker.qa.list_aliases"
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "alias"

  attribute {
    name = "alias"
    type = "S"
  }
}

resource "aws_dynamodb_table" "metadata-ddb-table" {
  name         = "teletracker.qa.metadata"
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "type"
  range_key = "id"

  attribute {
    name = "type"
    type = "S"
  }

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "slug"
    type = "S"
  }

  global_secondary_index {
    name            = "type-slug-index"
    hash_key        = "type"
    range_key       = "slug"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "id-mapping-ddb-table" {
  name         = "teletracker.qa.id_mapping"
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "externalId"

  attribute {
    name = "externalId"
    type = "S"
  }

  attribute {
    name = "id"
    type = "S"
  }

  global_secondary_index {
    name            = "id-to-externals"
    hash_key        = "id"
    range_key       = "externalId"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "scraped_items" {
  name         = "teletracker.qa.scraped_items"
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "id"
  range_key = "version"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "version"
    type = "N"
  }
}