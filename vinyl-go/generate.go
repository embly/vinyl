package vinyl

//go:generate protoc -I ../app/src/main/protobuf ../app/src/main/protobuf/messages.proto --go_out=plugins=grpc:.
