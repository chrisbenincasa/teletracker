#!/bin/bash

# cp ../scala-server/build.sbt build.sbt
rsync -av --progress --exclude=target/ --exclude=.bloop ../scala-server/project ../scala-server/build.sbt  .

docker build -t 302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker-build/server . -f Dockerfile.alpine

rm build.sbt
rm -rf project/