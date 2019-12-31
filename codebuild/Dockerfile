ARG BASE_IMAGE=openjdk:8
FROM ${BASE_IMAGE}

ARG SBT_VERSION=1.2.8
ARG SHA=f4b9fde91482705a772384c9ba6cdbb84d1c4f7a278fd2bfb34961cd9ed8e1d7
ARG BASE_URL=https://github.com/sbt/sbt/releases/download

ENV SBT_OPTS="\
    -Djava.net.preferIPv4Stack=true \
    -XX:+AggressiveOpts             \
    -XX:+UseParNewGC                \
    -XX:+UseConcMarkSweepGC         \
    -XX:+CMSParallelRemarkEnabled   \
    -XX:+CMSClassUnloadingEnabled   \
    -XX:ReservedCodeCacheSize=128m  \
    -XX:SurvivorRatio=128           \
    -XX:MaxTenuringThreshold=0      \
    -Xss8M                          \
    -Xms512M                        \
    -Xmx2G                          \
    -server                         \
    "

ENV PATH="/usr/local/bin:$PATH" \
    GPG_KEY="E3FF2839C048B25C084DEBE9B26995E310250568" \
    PYTHON_PIP_VERSION="19.1.1" \
    LC_ALL=C.UTF-8 \
    LANG=C.UTF-8

ENV DOCKER_VERSION="19.03.1" \
    DOCKER_COMPOSE_VERSION="1.24.0" \
    DOCKER_BUCKET="download.docker.com" \
    DOCKER_CHANNEL="stable" \
    DOCKER_SHA256="6e7d8e24ee46b13d7547d751696d01607d19c8224c1b2c867acc8c779e77734b" \
    DIND_COMMIT="3b5fac462d21ca164b3778647420016315289034"

RUN set -ex \
    && apt-get update -qqy \
    && apt-get install -qqy curl bc python3 python3-dev python3-pip python3-setuptools \
    && pip3 install awscli boto3

RUN set -ex \
    && curl -fSL "https://${DOCKER_BUCKET}/linux/static/${DOCKER_CHANNEL}/x86_64/docker-${DOCKER_VERSION}.tgz" -o docker.tgz \
    && echo "${DOCKER_SHA256} *docker.tgz" | sha256sum -c - \
    && tar --extract --file docker.tgz --strip-components 1  --directory /usr/local/bin/ \
    && rm docker.tgz \
    && docker -v \
    # set up subuid/subgid so that "--userns-remap=default" works out-of-the-box
    && addgroup dockremap \
    && useradd -g dockremap dockremap \
    && echo 'dockremap:165536:65536' >> /etc/subuid \
    && echo 'dockremap:165536:65536' >> /etc/subgid \
    && wget "https://raw.githubusercontent.com/docker/docker/${DIND_COMMIT}/hack/dind" -O /usr/local/bin/dind \
    && curl -L https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-Linux-x86_64 > /usr/local/bin/docker-compose \
    && chmod +x /usr/local/bin/dind /usr/local/bin/docker-compose \
    # Ensure docker-compose works
    && ls -la /usr/local/bin \
    && docker-compose version

VOLUME /var/lib/docker

COPY dockerd-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["dockerd-entrypoint.sh"]