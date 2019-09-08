package main

import (
	"testing"

	vinyl "github.com/embly/vinyl/vinyl-go"
	"github.com/embly/vinyl/vinyl-go/qm"
	proto "github.com/golang/protobuf/proto"
	"github.com/stretchr/testify/assert"
)

func TestBasic(t *testing.T) {
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
		t.Error(err)
	}
	defer db.Close()
	user := User{
		Id:    "whatever",
		Email: "max@max.com",
	}
	if err := db.Insert(&user); err != nil {
		t.Error(err)
	}

	user2 := User{
		Id:    "whoever",
		Email: "max2@max.com",
	}
	if err := db.Insert(&user2); err != nil {
		t.Error(err)
	}

	queryResponse := []User{}
	if err := db.ExecuteQuery(&queryResponse,
		qm.Field("id").Equals("whatever"),
		qm.Limit(1),
	); err != nil {
		t.Error(err)
	}
	user.XXX_sizecache = 0
	user2.XXX_sizecache = 0
	assert.Equal(t, queryResponse, []User{user})

	users := []User{}
	if err := db.ExecuteQuery(&users, nil); err != nil {
		t.Error(err)
	}

	user.XXX_sizecache = 0
	user2.XXX_sizecache = 0
	assert.Equal(t, users, []User{user, user2})

	pkUser := User{}
	if err := db.LoadRecord(&pkUser, "whoever"); err != nil {
		t.Error(err)
	}
	assert.Equal(t, pkUser, user2)

	userAgain := User{}
	if err := db.DeleteRecord(&userAgain, "whoever"); err != nil {
		t.Error(err)
	}

	pkUser = User{}
	err = db.LoadRecord(&pkUser, "whoever")
	assert.Equal(t, pkUser, User{})

	if err := db.DeleteWhere(&User{}, nil); err != nil {
		t.Error(err)
	}

	users = []User{}
	if err := db.ExecuteQuery(&users, nil); err != nil {
		t.Error(err)
	}
	assert.Equal(t, []User{}, users)
}