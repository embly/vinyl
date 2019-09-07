package main

//go:generate protoc -I . ./tables.proto --go_out=plugins=grpc:.

import (
	fmt "fmt"
	"log"

	vinyl "github.com/embly/vinyl/vinyl-go"
	"github.com/embly/vinyl/vinyl-go/qm"
	proto "github.com/golang/protobuf/proto"
)

type Query struct {
	And      []Query
	Field    string
	LessThan Value
	Equals   Value
	Matches  *Query
}
type Value struct {
	Int    int
	String string
}

func main() {

	db, err := vinyl.Connect("vinyl://max:password@localhost:8090/foo", vinyl.Metadata{
		Descriptor: proto.FileDescriptor("tables.proto"),
		Tables: []vinyl.Table{{
			Name:       "User",
			PrimaryKey: "id",
			Indexes: []vinyl.Index{{
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

	user2 := User{
		Id:    "whoever",
		Email: "max2@max.com",
	}
	if err := db.Insert(&user2); err != nil {
		log.Fatal(err)
	}

	queryResponse := User{}
	if err := db.First(&queryResponse,
		qm.Field("id").Equals("whoever")); err != nil {
		log.Fatal(err)
	}
	fmt.Println(queryResponse)
	if queryResponse.Email != "max@max.com" {
		log.Fatal("I have failed me")
	}

	// users := []User{}
	// if err := db.All(&users,
	// 	qm.Field("id").GreaterThan("a")); err != nil {
	// 	log.Fatal(err)
	// }

	// fmt.Println(users)

	// users := []User{}
	// log.Println(db.First(&user))
	// log.Println(db.All(&users))
	// c.Get("http://localhost:8090/start")
	// c.Post("http://localhost:8090")

}
