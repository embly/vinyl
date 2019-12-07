FROM centos:7

RUN yum -y update && yum clean all && mkdir -p /go && chmod -R 777 /go && \
    yum install -y centos-release-scl && \
    yum -y install git go-toolset-7-golang && yum clean all
RUN yum -y install yum install -y https://www.foundationdb.org/downloads/6.2.10/rhel6/installers/foundationdb-clients-6.2.10-1.el6.x86_64.rpm 

ENV GOPATH=/go \
    BASH_ENV=/opt/rh/go-toolset-7/enable \
    ENV=/opt/rh/go-toolset-7/enable \
    PROMPT_COMMAND=". /opt/rh/go-toolset-7/enable"
ENV PATH="/opt/rh/go-toolset-7/root/usr/bin:/opt/rh/go-toolset-7/root/usr/sbin${PATH:+:${PATH}}"

WORKDIR /go/src/github.com/embly/vinyl

RUN bash /opt/rh/go-toolset-7/enable && go get "github.com/apple/foundationdb/bindings/go/src/fdb"
COPY ./debug/main.go .
RUN go build -o fdbug
# # RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -a -tags netgo -ldflags '-w -extldflags "-static"' -o fdbug


FROM centos:7
LABEL version=0.0.10

RUN yum install -y java-1.8.0-openjdk-devel python git unzip wget which time \
    && yum install -y https://www.foundationdb.org/downloads/6.2.10/rhel6/installers/foundationdb-clients-6.2.10-1.el6.x86_64.rpm nmap \
    && wget http://dl.bintray.com/sbt/rpm/sbt-1.2.8.rpm \
    && yum install -y sbt-1.2.8.rpm \
    && rm sbt-1.2.8.rpm

RUN mkdir -p /usr/local/bin
COPY ./fdb/fdb_create_cluster_file.bash /usr/local/bin/fdb_create_cluster_file.bash

# ENV VERSION_NUMBER=2.6.60.0

WORKDIR /opt/app/

COPY ./fdb/client_entrypoint.sh /opt/entrypoint.sh

COPY --from=0 /go/src/github.com/embly/vinyl/fdbug /opt
CMD /opt/entrypoint.sh

# CMD sleep 2 \
#    && /usr/local/bin/fdb_create_cluster_file.bash 2


#    && ./gradlew generateProto \
#    && ./gradlew run
