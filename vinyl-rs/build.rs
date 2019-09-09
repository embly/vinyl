extern crate protoc_rust_grpc;
extern crate protoc_rust;

use protoc_rust::Customize;

fn main() {
    protoc_rust_grpc::run(protoc_rust_grpc::Args {
        out_dir: "src/proto",
        includes: &["../vinyl/src/main/protobuf"],
        input: &["../vinyl/src/main/protobuf/transport.proto"],
        rust_protobuf: false, // also generate protobuf messages, not just services
        ..Default::default()
    }).expect("protoc-rust-grpc");

}
