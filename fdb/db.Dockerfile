FROM centos:7
LABEL version=6.2.10

RUN yum install -y which initscripts rsync net-tools passwd https://www.foundationdb.org/downloads/6.2.10/rhel6/installers/foundationdb-clients-6.2.10-1.el6.x86_64.rpm https://www.foundationdb.org/downloads/6.2.10/rhel6/installers/foundationdb-server-6.2.10-1.el6.x86_64.rpm

USER root

COPY ./fdb/fdb_docker_start.bash /usr/lib/foundationdb/

ENTRYPOINT ["/bin/bash", "-c", "/usr/lib/foundationdb/fdb_docker_start.bash 2"]
