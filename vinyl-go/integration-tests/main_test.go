package main

import (
	fmt "fmt"
	"log"
	"sync"
	"testing"
	"time"

	vinyl "github.com/embly/vinyl/vinyl-go"
	"github.com/embly/vinyl/vinyl-go/qm"
	proto "github.com/golang/protobuf/proto"
	"github.com/stretchr/testify/assert"
)

func runAndMeasureMany(db *vinyl.DB) {
	var wg sync.WaitGroup
	for i := 0; i < 6; i++ {
		wg.Add(1)
		go func() {
			for i := 0; i < 20; i++ {
				pkUser := User{}
				st := time.Now()
				if err := db.LoadRecord(&pkUser, "whoever"); err != nil {
					log.Fatal(err)
				}
				fmt.Println(time.Now().Sub(st))
			}
			wg.Done()
		}()
	}
	wg.Wait()

}

func TestInit(t *testing.T) {
	db, err := vinyl.Connect("vinyl://max:password@localhost:8090/12", vinyl.Metadata{
		Descriptor: proto.FileDescriptor("tables.proto"),
		Records: []vinyl.Record{{
			Name:       "User",
			PrimaryKey: "id",
			Indexes: []vinyl.Index{{
				Field:  "email",
				Unique: true,
			}},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	db.Close()
}

func TestInsert(t *testing.T) {

	db, err := vinyl.Connect("vinyl://max:password@localhost:8090/40", vinyl.Metadata{
		Descriptor: proto.FileDescriptor("tables.proto"),
		Records: []vinyl.Record{{
			Name:       "User",
			PrimaryKey: "id",
			Indexes: []vinyl.Index{{
				Field:  "email",
				Unique: true,
			}},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	user := User{
		Id:    "whatever",
		Email: "max@max.com",
	}

	if err := db.Insert(&user); err != nil {
		t.Fatal(err)
	}

}

func TestBasic(t *testing.T) {

	db, err := vinyl.Connect("vinyl://max:password@localhost:8090/12", vinyl.Metadata{
		Descriptor: proto.FileDescriptor("tables.proto"),
		Records: []vinyl.Record{{
			Name:       "User",
			PrimaryKey: "id",
			Indexes: []vinyl.Index{{
				Field:  "email",
				Unique: true,
			}},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	user := User{
		Id:    "whatever",
		Email: "max@max.com",
	}
	fmt.Println("inserting")

	t1 := time.Now()
	if err := db.Insert(&user); err != nil {
		t.Fatal(err)
	}
	fmt.Println("over", time.Now().Sub(t1))

	runAndMeasureMany(db)

	user2 := User{
		Id:    "whoever",
		Email: "max2@max.com",
	}
	if err := db.Insert(&user2); err != nil {
		t.Fatal(err)
	}

	queryResponse := []User{}
	if err := db.ExecuteQuery(&queryResponse,
		qm.Or(
			qm.Field("email").Equals("max@max.com"),
			qm.Field("email").Equals("foo@bar.com"),
		),
		qm.Limit(10),
	); err != nil {
		t.Fatal(err)
	}
	user.XXX_sizecache = 0
	user2.XXX_sizecache = 0
	assert.Equal(t, queryResponse, []User{user})

	users := []User{}
	if err := db.ExecuteQuery(&users, nil); err != nil {
		t.Fatal(err)
	}

	user.XXX_sizecache = 0
	user2.XXX_sizecache = 0
	assert.Equal(t, users, []User{user, user2})

	pkUser := User{}
	if err := db.LoadRecord(&pkUser, "whoever"); err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, pkUser, user2)

	userAgain := User{}
	if err := db.DeleteRecord(&userAgain, "whoever"); err != nil {
		t.Fatal(err)
	}

	pkUser = User{}
	err = db.LoadRecord(&pkUser, "whoever")
	assert.Equal(t, pkUser, User{})

	if err := db.DeleteWhere(&User{}, nil); err != nil {
		t.Fatal(err)
	}

	users = []User{}
	_ = users
	if err := db.ExecuteQuery(&users, nil); err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, []User{}, users)
}