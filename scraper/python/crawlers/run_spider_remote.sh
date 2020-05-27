#!/usr/bin/env bash

set -uex

if ! aws ecs list-task-definitions --family-prefix "$1" | jq -e '.taskDefinitionArns[0]' > /dev/null
then
  echo "Could not find task definition family $1";
  exit 1;
fi

SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=vpc-09a64ee30f2e3e82e | jq -r '.Subnets | map(.SubnetId) | join(",")')
SEC_GROUPS=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$1 Fargate" | jq -r '.SecurityGroups | map(.GroupId) | join(",")')

aws ecs run-task \
  --cluster teletracker-qa \
  --task-definition "$1" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SEC_GROUPS],assignPublicIp=ENABLED}"