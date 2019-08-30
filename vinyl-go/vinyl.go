package vinyl

import (
	"bytes"
	"crypto/tls"
	"fmt"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"reflect"

	"github.com/embly/vinyl/vinyl-go/descriptor"
	"github.com/embly/vinyl/vinyl-go/transport"
	proto "github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
	"golang.org/x/net/http2"
	"golang.org/x/net/publicsuffix"
)

// DB is an instance of a connection to the Record Layer database
type DB struct {
	httpClient *http.Client
	hostname   string
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

func newClient() *DB {
	jar, err := cookiejar.New(&cookiejar.Options{PublicSuffixList: publicsuffix.List})
	if err != nil {
		log.Fatal(err)
	}
	return &DB{
		httpClient: &http.Client{
			Jar: jar,
			Transport: &http2.Transport{
				// So http2.Transport doesn't complain the URL scheme isn't 'https'
				AllowHTTP: true,
				// Pretend we are dialing a TLS endpoint.
				// Note, we ignore the passed tls.Config
				DialTLS: func(network, addr string, cfg *tls.Config) (net.Conn, error) {
					return net.Dial(network, addr)
				},
			},
		},
	}
}

// Connect ...
func Connect(connectionString string, metadata Metadata) (db *DB, err error) {
	db = newClient()
	u, err := url.Parse(connectionString)
	if err != nil {
		return
	}
	db.hostname = u.Hostname()
	if u.Port() != "" {
		db.hostname += ":" + u.Port()
	}

	if u.Scheme != "vinyl" {
		err = errors.Errorf("Connection url has incorrect scheme value '%s', should be vinyl://", u.Scheme)
		return
	}

	password, _ := u.User.Password()
	request := transport.Request{
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
		request.Tables = append(request.Tables, &table)
	}

	b, err := descriptor.AddRecordTypeUnion(metadata.Descriptor, tableNames)
	if err != nil {
		err = errors.Wrap(err, "error parsing descriptor")
		return
	}
	request.FileDescriptor = b

	_, err = db.sendRequest(request, "start")
	return
}

// Close ...
func (db *DB) Close() (err error) {
	db.httpClient.CloseIdleConnections()
	return nil
}

func (db *DB) First(msg proto.Message, tmp ...string) (err error) {
	query := transport.Query{
		Filter: &transport.QueryComponent{
			ComponentType: transport.QueryComponent_FIELD,
			Field: &transport.Field{
				Name: "email",
				Value: &transport.Value{
					String_:   "max@max.com",
					ValueType: transport.Value_STRING,
				},
			},
		},
		RecordType: proto.MessageName(msg),
	}
	fmt.Println(query.RecordType)
	request := transport.Request{
		Query: &query,
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

func (db *DB) All(msgs interface{}, query ...string) (err error) {
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

func (db *DB) sendRequest(query transport.Request, path string) (respProto transport.Response, err error) {
	b, err := proto.Marshal(&query)
	if err != nil {
		return
	}

	fmt.Println(proto.Unmarshal(b, &query))
	return db.responseWrapper(db.httpClient.Post(
		fmt.Sprintf("http://%s/%s", db.hostname, path),
		"application/protobuf",
		bytes.NewBuffer(b),
	))
}

func (db *DB) responseWrapper(resp *http.Response, err error) (transport.Response, error) {
	respProto := transport.Response{}
	if err != nil {
		return respProto, err
	}
	b, _ := ioutil.ReadAll(resp.Body)
	if err := proto.Unmarshal(b, &respProto); err != nil {
		return respProto, errors.Wrap(err, "error unmarshalling the database response")
	}
	if respProto.Error != "" {
		return respProto, errors.New(respProto.Error)
	}
	// TODO: protobuf responses
	if resp.StatusCode > 299 {
		return respProto, errors.Errorf("Got error response from server: %d %s", resp.StatusCode, (b))
	}
	return respProto, nil
}
