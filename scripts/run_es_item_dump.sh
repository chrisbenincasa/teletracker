#!/usr/bin/env bash

set -uex

SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=vpc-09a64ee30f2e3e82e | jq -r '.Subnets | map(.SubnetId) | join(",")')
SEC_GROUPS=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$1 Fargate" | jq -r '.SecurityGroups | map(.GroupId) | join(",")')

aws ecs run-task \
  --cluster teletracker-qa \
  --task-definition "elasticdump" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SEC_GROUPS],assignPublicIp=ENABLED}"