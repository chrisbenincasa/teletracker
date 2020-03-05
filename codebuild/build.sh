#!/bin/bash

# cp ../backend/build.sbt build.sbt
rsync -av --progress --exclude=target/ --exclude=.bloop ../backend/project ../backend/build.sbt  .

docker build -t 302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker-build/server . -f Dockerfile

rm build.sbt
rm -rf project/