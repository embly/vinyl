set -Eeuxo pipefail

cd "$(dirname ${BASH_SOURCE[0]})"

go test -v .
