// Package vinyl is a Go library to connect to Vinyl and the FoundationDB Record Layer.
package vinyl

import (
	"context"
	"fmt"
	"net/url"
	"reflect"

	"github.com/embly/vinyl/vinyl-go/descriptor"
	"github.com/embly/vinyl/vinyl-go/qm"
	"github.com/embly/vinyl/vinyl-go/transport"
	proto "github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
	"google.golang.org/grpc"
)

var ErrNoRows = errors.New("vinyl: no rows in result set")

// DB is an instance of a connection to the Record Layer database
type DB struct {
	client   transport.VinylClient
	grpcConn *grpc.ClientConn
	hostname string
	token    string
}

// Record defines a Record Layer record type
type Record struct {
	Name       string
	PrimaryKey string
	Indexes    []Index
}

// Index defines a Record Layer index
type Index struct {
	Field  string
	Unique bool
}

// Metadata defines the proto file descriptor and related record and index data
type Metadata struct {
	Descriptor []byte
	Records    []Record
}

// Connect connects to a vinyl server and returns a DB instance.
//    db, err := vinyl.Connect("vinyl://max:password@localhost:8090/foo", vinyl.Metadata{
//    	Descriptor: proto.FileDescriptor("tables.proto"),
//    	Records: []vinyl.Record{{
//    		Name:       "User",
//    		PrimaryKey: "id",
//    		Indexes: []vinyl.Index{{
//    			Field:  "email",
//    			Unique: true,
//    		}},
//    	}},
//    })
func Connect(connectionString string, metadata Metadata) (db *DB, err error) {
	u, err := url.Parse(connectionString)
	if err != nil {
		return
	}
	if u.Scheme != "vinyl" {
		err = errors.Errorf("Connection url has incorrect scheme value '%s', should be vinyl://", u.Scheme)
		return
	}

	db = &DB{}
	db.hostname = u.Hostname()
	if u.Port() != "" {
		db.hostname += ":" + u.Port()
	}
	fmt.Println(db.hostname)
	conn, err := grpc.Dial(db.hostname, grpc.WithInsecure())
	if err != nil {
		return
	}
	client := transport.NewVinylClient(conn)
	db = &DB{
		client:   client,
		grpcConn: conn,
	}

	password, _ := u.User.Password()
	loginRequest := transport.LoginRequest{
		Username: u.User.Username(),
		Password: password,
		Keyspace: u.Path,
	}
	recordNames := make([]string, len(metadata.Records))
	for i, t := range metadata.Records {
		recordNames[i] = t.Name
		record := transport.Record{
			Name: t.Name,
			FieldOptions: map[string]*transport.FieldOptions{
				t.PrimaryKey: &transport.FieldOptions{
					PrimaryKey: true,
				},
			},
		}
		for _, idx := range t.Indexes {
			v := record.FieldOptions[idx.Field]
			if v == nil {
				v = &transport.FieldOptions{}
			}
			v.Index = &transport.FieldOptions_IndexOption{
				Type:   "value",
				Unique: idx.Unique,
			}
			record.FieldOptions[idx.Field] = v
		}
		loginRequest.Records = append(loginRequest.Records, &record)
	}
	fmt.Println("do I get here?")
	b, err := descriptor.AddRecordTypeUnion(metadata.Descriptor, recordNames)
	if err != nil {
		err = errors.Wrap(err, "error parsing descriptor")
		return
	}
	loginRequest.FileDescriptor = b
	fmt.Println("here?", loginRequest)
	resp, err := client.Login(context.Background(), &loginRequest)
	if err != nil {
		return
	}
	if resp.Error != "" {
		err = errors.New(resp.Error)
		return
	}
	db.token = resp.Token
	return
}

// Close closes the underlying grpc connection to the vinyl server
func (db *DB) Close() (err error) {
	db.grpcConn.Close()
	return nil
}

func (db *DB) executeQuery(recordType string, query qm.QueryComponent, queryProperty qm.QueryProperty) (respProto *transport.Response, err error) {
	rq := transport.RecordQuery{}
	if qc, ok := query.(qm.QueryComponent); ok {
		filter, errs := qc.QueryComponent()
		if len(errs) != 0 {
			// TODO: combine errors
			err = errs[0]
			return
		}
		rq.Filter = filter
	}
	request := transport.Request{
		Query: &transport.Query{
			QueryType: transport.Query_RECORD_QUERY,
			ExecuteProperties: &transport.ExecuteProperties{
				Limit: int32(queryProperty.Limit),
				Skip:  int32(queryProperty.Skip),
			},
			RecordQuery: &rq,
			RecordType:  recordType,
		},
	}
	fmt.Println(request)
	return db.sendRequest(request, "")
}

// LoadRecord loads a single record using its primary key value. You must pass a struct of the
// proto message type for underlying record. vinyl-go uses "proto.MessageName(msg)" to get the
// name of the record type
//    user := User{}
//    if err := db.LoadRecord(&user, "primary_key"); err != nil {
//    	t.Error(err)
//    }
func (db *DB) LoadRecord(msg proto.Message, pk interface{}) (err error) {
	value, err := qm.ValueForInterface(pk)
	if err != nil {
		return
	}
	request := transport.Request{
		Query: &transport.Query{
			QueryType:  transport.Query_LOAD_RECORD,
			PrimaryKey: value,
			RecordType: proto.MessageName(msg),
		},
	}
	resp, err := db.sendRequest(request, "")
	if err != nil {
		return err
	}
	if len(resp.Records) > 0 {
		return proto.Unmarshal(resp.Records[0], msg)
	}
	return nil
}

// DeleteRecord deletes a record using its primary key. You must pass a struct of the
// proto message type for underlying record. vinyl-go uses "proto.MessageName(msg)" to get the
// name of the record type
//    user := User{}
//    if err := db.DeleteRecord(&user, "whoever"); err != nil {
//    	t.Error(err)
//    }
func (db *DB) DeleteRecord(msg proto.Message, pk interface{}) (err error) {
	value, err := qm.ValueForInterface(pk)
	if err != nil {
		return
	}
	request := transport.Request{
		Query: &transport.Query{
			QueryType:  transport.Query_DELETE_RECORD,
			PrimaryKey: value,
			RecordType: proto.MessageName(msg),
		},
	}
	if _, err := db.sendRequest(request, ""); err != nil {
		return err
	}
	return nil
}

func (db *DB) DeleteWhere(msg proto.Message, query qm.QueryComponent) (err error) {
	rq := transport.RecordQuery{}
	if qc, ok := query.(qm.QueryComponent); ok {
		filter, errs := qc.QueryComponent()
		if len(errs) != 0 {
			// TODO: combine errors
			err = errs[0]
			return
		}
		rq.Filter = filter
	}
	request := transport.Request{
		Query: &transport.Query{
			QueryType:   transport.Query_DELETE_WHERE,
			RecordType:  proto.MessageName(msg),
			RecordQuery: &rq,
		},
	}
	if _, err := db.sendRequest(request, ""); err != nil {
		return err
	}
	return nil

}

// ExecuteQuery executes a query and returns the matching records
//    queryResponse := []User{}
//    if err := db.ExecuteQuery(&queryResponse,
//    	qm.Or(
//    		qm.Field("email").Equals("max@max.com"),
//    		qm.Field("email").Equals("foo@bar.com"),
//    	),
//    	qm.Limit(10),
//    ); err != nil {
//    	t.Error(err)
//    }
func (db *DB) ExecuteQuery(msgs interface{}, query qm.QueryComponent, queryProperites ...qm.QueryProperty) (err error) {
	queryProperty := qm.QueryProperty{}
	for _, qp := range queryProperites {
		if qp.Skip != 0 {
			queryProperty.Skip = qp.Skip
		}
		if qp.Limit != 0 {
			queryProperty.Limit = qp.Limit
		}
	}

	v := reflect.ValueOf(msgs)
	if v.Kind() != reflect.Ptr {
		return errors.Errorf("must be passed a pointer to a slice %v", v.Type())
	}
	v = v.Elem()
	recordType := proto.MessageName(reflect.New(v.Type().Elem()).Interface().(proto.Message))

	respProto, err := db.executeQuery(recordType, query, queryProperty)
	if err != nil {
		return
	}
	size := len(respProto.Records)
	v.Set(reflect.MakeSlice(v.Type(), size, size))
	for i := 0; i < size; i++ {
		proto.Unmarshal(
			respProto.Records[i],
			v.Index(i).Addr().Interface().(proto.Message),
		)
	}
	return nil
}

// Insert inserts a record
//    user := User{
//    	Id:    "whatever",
//    	Email: "max@max.com",
//    }
//    if err := db.Insert(&user); err != nil {
//    	t.Error(err)
//    }
func (db *DB) Insert(msg proto.Message) (err error) {
	request := transport.Request{}
	b, err := proto.Marshal(msg)
	if err != nil {
		errors.Wrap(err, "error marshalling proto message")
	}
	request.Insertions = append(request.Insertions, &transport.Insert{
		Record: proto.MessageName(msg),
		Data:   b,
	})
	_, err = db.sendRequest(request, "")
	return
}

func (db *DB) sendRequest(query transport.Request, path string) (respProto *transport.Response, err error) {
	query.Token = db.token
	respProto, err = db.client.Query(context.Background(), &query)
	if err != nil {
		return
	}
	if respProto.Error != "" {
		err = errors.New(respProto.Error)
	}
	return
}
