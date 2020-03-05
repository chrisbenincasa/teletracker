#!/usr/bin/env bash

if [ $(ps | grep -q "[c]loud_sql_proxy") ]; then
  echo "cloud_sql_proxy is running!"
  exit 1
fi

docker-compose up -d db
./sbt resetDb