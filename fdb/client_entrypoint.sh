#!/usr/bin/env bash

set -ex

sleep 2

/usr/local/bin/fdb_create_cluster_file.bash 2

cp /etc/foundationdb/fdb.cluster /opt/app

sleep 100000
# sbt ~reStart
