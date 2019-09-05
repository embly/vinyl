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

// DB is an instance of a connection to the Record Layer database
type DB struct {
	client   transport.VinylClient
	grpcConn *grpc.ClientConn
	hostname string
	token    string
}

// Table defines a Record Layer table
type Table struct {
	Name       string
	PrimaryKey string
	Indexes    []Index
}

// Index defines a Record Layer index
type Index struct {
	Field  string
	Unique bool
}

// Metadata defines the proto file descriptor and related table and index data
type Metadata struct {
	Descriptor []byte
	Tables     []Table
}

// Connect ...
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
	tableNames := make([]string, len(metadata.Tables))
	for i, t := range metadata.Tables {
		tableNames[i] = t.Name
		table := transport.Table{
			Name: t.Name,
			FieldOptions: map[string]*transport.FieldOptions{
				t.PrimaryKey: &transport.FieldOptions{
					PrimaryKey: true,
				},
			},
		}
		for _, idx := range t.Indexes {
			v := table.FieldOptions[idx.Field]
			if v == nil {
				v = &transport.FieldOptions{}
			}
			v.Index = &transport.FieldOptions_IndexOption{
				Type:   "value",
				Unique: idx.Unique,
			}
			table.FieldOptions[idx.Field] = v
		}
		loginRequest.Tables = append(loginRequest.Tables, &table)
	}

	b, err := descriptor.AddRecordTypeUnion(metadata.Descriptor, tableNames)
	if err != nil {
		err = errors.Wrap(err, "error parsing descriptor")
		return
	}
	loginRequest.FileDescriptor = b
	resp, err := client.Login(context.Background(), &loginRequest)
	if resp.Error != "" {
		err = errors.New(resp.Error)
		return
	}
	db.token = resp.Token
	return
}

// Close ...
func (db *DB) Close() (err error) {
	db.grpcConn.Close()
	return nil
}

func (db *DB) First(msg proto.Message, query qm.QueryComponent) (err error) {
	qc, errs := query.QueryComponent()
	if len(errs) != 0 {
		// TODO: combine errors
		return errs[0]
	}
	q := transport.Query{
		Filter:     qc,
		RecordType: proto.MessageName(msg),
	}
	fmt.Println(q.RecordType)
	request := transport.Request{
		Query: &q,
	}
	respProto, err := db.sendRequest(request, "")
	if err != nil {
		return
	}
	if len(respProto.Records) > 0 {
		return proto.Unmarshal(respProto.Records[0], msg)
	}
	return nil
}

func (db *DB) All(msgs interface{}, query qm.QueryComponent) (err error) {
	rv := reflect.ValueOf(msgs)
	msgType := rv.Elem().Type().Elem()
	val := reflect.New(msgType).Interface()
	name := proto.MessageName(val.(proto.Message))
	fmt.Println(name)
	return nil
}

// Insert ...
func (db *DB) Insert(msg proto.Message) (err error) {
	request := transport.Request{}
	b, err := proto.Marshal(msg)
	if err != nil {
		errors.Wrap(err, "error marshalling proto message")
	}
	request.Insertions = append(request.Insertions, &transport.Insert{
		Table: proto.MessageName(msg),
		Data:  b,
	})
	_, err = db.sendRequest(request, "")
	return
}

func (db *DB) sendRequest(query transport.Request, path string) (respProto *transport.Response, err error) {
	query.Token = db.token
	queryClient, err := db.client.Query(context.Background(), &query)
	if err != nil {
		return
	}
	resp, err := queryClient.Recv()
	if err != nil {
		return
	}
	if resp.Error != "" {
		err = errors.New(resp.Error)
	}
	return resp, err
}
