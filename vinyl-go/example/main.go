package main

//go:generate protoc -I . ./tables.proto --go_out=plugins=grpc:.

import (
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

// Query.and(
// 	Query.field("price").lessThan(50),
// 	Query.field("flower").matches(Query.field("type").equalsValue(FlowerType.ROSE.name())))
func main() {
	_ = Query{
		And: []Query{
			{Field: "price", LessThan: Value{Int: 50}},
			{Field: "flower", Matches: &Query{
				Field:  "type",
				Equals: Value{String: "foo"},
			}},
		},
	}

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
		Email: "max@max.com",
	}
	if err := db.Insert(&user2); err != nil {
		log.Fatal(err)
	}

	queryResponse := User{}
	if err := db.First(&queryResponse,
		qm.Field("email").Equals("max@max.com")); err != nil {
		log.Fatal(err)
	}
	if queryResponse.Id != "whatever" {
		log.Fatal("I have failed me")
	}

	users := []User{}
	if err := db.All(&users,
		qm.Field("email").Equals("max@max.com")); err != nil {
		log.Fatal(err)
	}

	// users := []User{}
	// log.Println(db.First(&user))
	// log.Println(db.All(&users))
	// c.Get("http://localhost:8090/start")
	// c.Post("http://localhost:8090")

}
