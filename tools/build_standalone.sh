set -Eeuxo pipefail

cd "$(dirname ${BASH_SOURCE[0]})"

docker-compose build client standalone

docker --config ~/.docker-embly push embly/vinyl