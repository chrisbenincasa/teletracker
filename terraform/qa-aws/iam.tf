resource "aws_iam_policy" "kms_encrypt_decrypt_policy" {
  name        = "KMS_Encrypt_Decrypt"
  path        = "/"
  description = "Allows for KMS Encrypt and Decrypt using all CMK"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": {
    "Effect": "Allow",
    "Action": [
      "kms:Encrypt",
      "kms:Decrypt"
    ],
    "Resource": [
      "arn:aws:kms:*:302782651551:key/*"
    ]
  }
}
EOF
}

data "aws_iam_policy" "ssm_read_only_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess"
}

data "aws_iam_policy" "kms_power_user_policy" {
  arn = "arn:aws:iam::aws:policy/AWSKeyManagementServicePowerUser"
}