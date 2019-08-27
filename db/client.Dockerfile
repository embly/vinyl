FROM centos:7
LABEL version=0.0.10

RUN yum install -y java-1.8.0-openjdk-devel python git unzip wget which time \
    && yum install -y https://www.foundationdb.org/downloads/6.0.15/rhel6/installers/foundationdb-clients-6.0.15-1.el6.x86_64.rpm nmap \
    && wget http://dl.bintray.com/sbt/rpm/sbt-1.2.8.rpm \
    && yum install -y sbt-1.2.8.rpm \
    && rm sbt-1.2.8.rpm

RUN mkdir -p /usr/local/bin
COPY ./db/fdb_create_cluster_file.bash /usr/local/bin/fdb_create_cluster_file.bash

# ENV VERSION_NUMBER=2.6.60.0

WORKDIR /opt/app/

COPY ./db/client_entrypoint.sh /opt/entrypoint.sh

CMD /opt/entrypoint.sh

# CMD sleep 2 \
#    && /usr/local/bin/fdb_create_cluster_file.bash 2


#    && ./gradlew generateProto \
#    && ./gradlew run
