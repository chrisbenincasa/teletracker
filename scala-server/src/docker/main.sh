#!/usr/bin/env bash

# location the fat jar
BIN_JAR=`ls /app/bin/*.jar | head`

echo $@

LOG_PATH="/var/log/teletracker"

mkdir -p ${LOG_PATH}

JVM_OPTS="""
    -server \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled \
    -Xmx512m -Xms512m \
    -Dlog.service.output=${LOG_PATH}/service.log \
    -Dlog.access.output=${LOG_PATH}/access.log \
    -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark \
    -Dsun.net.inetaddr.ttl=5
"""

exec java $JVM_OPTS $JVM_ARGS -jar ${BIN_JAR} $@