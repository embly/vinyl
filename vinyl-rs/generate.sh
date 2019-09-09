set -Eeuxo pipefail

cd "$(dirname ${BASH_SOURCE[0]})"

cargo build --features="build_protos" --bin proto
