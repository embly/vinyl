#! /bin/bash

set -eux

# https://github.com/apple/foundationdb/tree/master/packaging/docker/samples/local
export FDB_NETWORKING_MODE=host
FDB_CLUSTER_FILE="${FDB_CLUSTER_FILE:-'/var/fdb/fdb.cluster'}"
# export FDB_COORDINATOR=`hostname`
source /var/fdb/scripts/create_server_environment.bash
create_server_environment
source /var/fdb/.fdbenv
echo "Starting FDB server on $PUBLIC_IP:$FDB_PORT"
fdbserver --listen_address 0.0.0.0:$FDB_PORT --public_address $PUBLIC_IP:$FDB_PORT \
        --datadir /var/fdb/data --logdir /var/fdb/logs \
        --locality_zoneid=`hostname` --locality_machineid=`hostname` --class $FDB_PROCESS_CLASS &
sleep 3

if ! fdbcli -C $FDB_CLUSTER_FILE --exec status --timeout 1 ; then
    if ! fdbcli -C $FDB_CLUSTER_FILE --exec "configure new single memory ; status" --timeout 10 ; then 
        echo "Unable to configure new FDB cluster."
        exit 1
    fi
fi

java -jar scl-assembly-1.0.jar
