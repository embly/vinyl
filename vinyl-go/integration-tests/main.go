package main

import (
	"log"
)

//go:generate protoc -I . ./tables.proto --go_out=plugins=grpc:.

func main() {
	log.Fatal("run this package with go test")
}
