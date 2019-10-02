extern crate protoc_rust;

use protoc_rust::Customize;

fn main() {
    protoc_rust::run(protoc_rust::Args {
        out_dir: "src/proto",
        includes: &["../vinyl/src/main/protobuf"],
        input: &["../vinyl/src/main/protobuf/transport.proto"],
        customize: Customize {
            ..Default::default()
        },
    })
    .expect("protoc");

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
