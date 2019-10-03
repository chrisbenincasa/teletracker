#!/usr/bin/env bash

# location the fat jar
BIN_JAR=$(find /app/bin -maxdepth 1 -name '*.jar' | head)

LOG_PATH="/var/log/teletracker"

mkdir -p ${LOG_PATH}

./cloud_sql_proxy -instances="${SQL_INSTANCE}"=tcp:5432 &> ${LOG_PATH}/cloud_sql_proxy.log &

JVM_OPTS="""-server \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled \
    -Xmx1g -Xms1g \
    -Dlog.service.output=${LOG_PATH}/service.log \
    -Dlog.access.output=${LOG_PATH}/access.log \
    -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark \
    -Dsun.net.inetaddr.ttl=5
"""

berglas exec --local -- java ${JVM_OPTS} ${JVM_ARGS} -jar ${BIN_JAR} $@