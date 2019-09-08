extern crate protoc_rust;

use protoc_rust::Customize;

fn main() {
    protoc_rust::run(protoc_rust::Args {
        out_dir: "src/proto",
        input: &["../../vinyl-go/example/tables.proto"],
        includes: &["../../vinyl-go/example/"],
        customize: Customize {
            ..Default::default()
        },
    })
    .expect("protoc");
}
