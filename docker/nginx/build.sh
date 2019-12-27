#!/bin/bash

TAG=$1

CERT_ARN='arn:aws:acm:us-west-1:302782651551:certificate/9c01d0f1-420b-48b1-bebc-c8bd3d3c44d6'
SSM_PARAM_NAME='api.qa.teletracker.tv-ssl-private-key'

aws acm get-certificate --certificate-arn "$CERT_ARN" | jq -r '.CertificateChain' > fullchain.pem
aws ssm get-parameter --name "$SSM_PARAM_NAME" --with-decryption | jq -r '.Parameter.Value' > privkey.pem

docker build -t 302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker/server-nginx:$TAG .

docker tag 302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker/server-nginx:$TAG 302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker/server-nginx:latest

rm fullchain.pem privkey.pem

echo "Built 302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker/server-nginx:$TAG"
