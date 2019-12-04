- add schema validation (and review related guide)
- catch more errors, extend query types
- make dropping grpc allowed and allow vinyl-rs DB to take a different connection mechanism

for embly

- proto and hcl defined schema and indexes
- create first basic app with user accounts and metadata

vinly-rs-core -> query planning and converting inserts and queries to the underlying protobuf definitions
vinyl-rs -> the grpc implementation
vinyl-rs-embly -> an embly specific


- ask on foundationdb forum about basic latency and connection
caching strategies. should the context be applied to an in-memory db?
should the metedata be applied once?

Also figure out how to get instrumentng on basic foundationdb queries
so see what kind of activity is being generated that can be cut back 

if the overhead here is actually significant it might be worth looking
elsewhere for basic KV srtorage