- add schema validation (and review related guide)
- catch more errors, extend query types
- make dropping grpc allowed and allow vinyl-rs DB to take a different connection mechanism

for embly

- proto and hcl defined schema and indexes
- create first basic app with user accounts and metadata

vinly-rs-core -> query planning and converting inserts and queries to the underlying protobuf definitions
vinyl-rs -> the grpc implementation
vinyl-rs-embly -> an embly specific
