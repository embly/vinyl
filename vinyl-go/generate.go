package vinyl

//go:generate protoc -I ../vinyl/src/main/protobuf ../vinyl/src/main/protobuf/messages.proto --go_out=plugins=grpc:.
