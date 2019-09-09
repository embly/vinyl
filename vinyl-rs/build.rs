extern crate protoc_rust_grpc;
extern crate protoc_rust;

use protoc_rust::Customize;

fn main() {
    protoc_rust_grpc::run(protoc_rust_grpc::Args {
        out_dir: "src/proto",
        includes: &["../vinyl/src/main/protobuf"],
        input: &["../vinyl/src/main/protobuf/transport.proto"],
        rust_protobuf: true, // also generate protobuf messages, not just services
        ..Default::default()
    }).expect("protoc-rust-grpc");

    protoc_rust::run(protoc_rust::Args {
        out_dir: "src/proto",
        input: &["../proto/example.proto"],
        includes: &["../proto/"],
        customize: Customize {
            ..Default::default()
        },
    })
    .expect("protoc");

}
