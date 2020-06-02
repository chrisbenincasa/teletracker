#!/bin/bash

TAG=$1

docker build -t 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/elasticdump:$TAG .

docker tag 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/elasticdump:$TAG 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/elasticdump:latest

echo "Built 302782651551.dkr.ecr.us-west-2.amazonaws.com/teletracker/elasticdump:$TAG"
