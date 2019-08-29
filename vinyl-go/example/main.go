package main

//go:generate protoc -I . ./tables.proto --go_out=plugins=grpc:.

import (
	"log"

	vinyl "github.com/embly/vinyl/vinyl-go"
	md "github.com/embly/vinyl/vinyl-go/metadata"
	proto "github.com/golang/protobuf/proto"
)

func main() {

	db, err := vinyl.Connect("vinyl://max:password@localhost:8090/foo", md.Metadata{
		Descriptor: proto.FileDescriptor("tables.proto"),
		Tables: []md.Table{{
			Name:       "User",
			PrimaryKey: "id",
			Indexes: []md.Index{{
				Field:  "email",
				Unique: true,
			}},
		}},
	})
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()
	user := User{
		Id:    "whatever",
		Email: "max@max.com",
	}
	if err := db.Insert(&user); err != nil {
		log.Fatal(err)
	}
	// c.Get("http://localhost:8090/start")
	// c.Post("http://localhost:8090")

}
