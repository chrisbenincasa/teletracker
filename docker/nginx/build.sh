#!/bin/bash

TAG=$1

docker build -t 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/server-nginx:$TAG .

docker tag 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/server-nginx:$TAG 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/server-nginx:latest

echo "Built 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/server-nginx:$TAG"
