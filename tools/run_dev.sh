set -Eeuxo pipefail

cd "$(dirname ${BASH_SOURCE[0]})"

docker-compose down
docker-compose up -d client fdb
docker-compose logs -f &
sleep 5
docker-compose exec client sbt ~reStart