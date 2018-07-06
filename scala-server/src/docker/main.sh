#!/usr/bin/env bash

# location the fat jar
BIN_JAR=`ls /app/bin/*.jar | head`

printenv

JVM_OPTS="""
    -server \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled \
    -Xmx512m -Xms512m \
    -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark \
    -Dsun.net.inetaddr.ttl=5
"""

exec java $JVM_OPTS $JVM_ARGS -jar ${BIN_JAR} $@