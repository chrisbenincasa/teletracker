FROM openjdk:8u141-jre-slim

ADD https://storage.googleapis.com/berglas/master/linux_amd64/berglas /bin/berglas
RUN chmod +x /bin/berglas

RUN apt-get update

RUN apt-get install -y net-tools git wget curl

RUN wget https://dl.google.com/cloudsql/cloud_sql_proxy.linux.amd64 -O /cloud_sql_proxy
RUN chmod +x cloud_sql_proxy

# Uncomment if we need to install certs for SSL access to the database... think of a good
# way to store the certs if we ever want to do this.
# ADD ./certs /certs
# ADD ./install-certs /install-certs
# RUN /install-certs
