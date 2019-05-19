FROM centos:7
LABEL version=0.0.10

RUN yum install -y java-1.8.0-openjdk-devel python git unzip wget which time
RUN yum install -y https://www.foundationdb.org/downloads/6.0.15/rhel6/installers/foundationdb-clients-6.0.15-1.el6.x86_64.rpm nmap

RUN mkdir -p /usr/local/bin
COPY fdb_create_cluster_file.bash /usr/local/bin/fdb_create_cluster_file.bash

RUN mkdir -p /opt/gradle/ \
    && wget https://services.gradle.org/distributions/gradle-5.1-bin.zip -P /tmp \
    && unzip /tmp/gradle-5.1-bin.zip -d /opt/gradle/

ENV PATH="${PATH}:/opt/gradle/gradle-5.1/bin:/usr/local/bin"
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
# ENV VERSION_NUMBER=2.6.60.0

WORKDIR /opt/app/

CMD sleep 2 \
    && /usr/local/bin/fdb_create_cluster_file.bash 2 \
    && ./gradlew generateProto \
    && ./gradlew run
