set -Eeuxo pipefail

cd "$(dirname ${BASH_SOURCE[0]})"

cargo run --features="build_protos" --bin proto
