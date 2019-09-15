FROM gcr.io/cloud-builders/curl@sha256:5d16463a65786065a4f5c46d083c836f67a73dc33d795dab12e8404810c4e42f

RUN apt-get update && \
    apt-get -y install ca-certificates && \
    apt-get -y install jq && \
    rm -rf /var/lib/apt/lists/*

ENTRYPOINT [ "/usr/bin/jq" ]