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

	queryResponse := []User{}
	if err := db.ExecuteQuery(&queryResponse,
		qm.Field("id").Equals("whatever"),
		qm.Limit(1),
	); err != nil {
		log.Fatal(err)
	}

	if queryResponse[0].Email != "max@max.com" {
		log.Fatal("I have failed me")
	}

	users := []User{}
	if err := db.ExecuteQuery(&users, nil); err != nil {
		log.Fatal(err)
	}

	fmt.Println(users)

	// users := []User{}
	// log.Println(db.First(&user))
	// log.Println(db.All(&users))
	// c.Get("http://localhost:8090/start")
	// c.Post("http://localhost:8090")

}
