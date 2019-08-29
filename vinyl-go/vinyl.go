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

	"github.com/embly/vinyl/vinyl-go/descriptor"
	md "github.com/embly/vinyl/vinyl-go/metadata"
	proto "github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
	"golang.org/x/net/http2"
	"golang.org/x/net/publicsuffix"
)

type DB struct {
	httpClient *http.Client
	hostname   string
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
func Connect(connectionString string, metadata md.Metadata) (db *DB, err error) {
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
	query := Query{
		Username: u.User.Username(),
		Password: password,
		Keyspace: u.Path,
	}
	tableNames := make([]string, len(metadata.Tables))
	for i, t := range metadata.Tables {
		tableNames[i] = t.Name
		table := Table{
			Name: t.Name,
			FieldOptions: map[string]*FieldOptions{
				t.PrimaryKey: &FieldOptions{
					PrimaryKey: true,
				},
			},
		}
		for _, idx := range t.Indexes {
			v := table.FieldOptions[idx.Field]
			if v == nil {
				v = &FieldOptions{}
			}
			v.Index = &FieldOptions_IndexOption{
				Type:   "value",
				Unique: idx.Unique,
			}
			table.FieldOptions[idx.Field] = v
		}
		query.Tables = append(query.Tables, &table)
	}

	b, err := descriptor.AddRecordTypeUnion(metadata.Descriptor, tableNames)
	if err != nil {
		err = errors.Wrap(err, "error parsing descriptor")
		return
	}
	query.FileDescriptor = b

	err = db.sendQuery(query, "start")
	return
}

// Close ...
func (db *DB) Close() (err error) {
	db.httpClient.CloseIdleConnections()
	return nil
}

// Insert ...
func (db *DB) Insert(msg proto.Message) (err error) {
	query := Query{}
	b, err := proto.Marshal(msg)
	if err != nil {
		errors.Wrap(err, "error marshalling proto message")
	}
	query.Insertions = append(query.Insertions, &Insert{
		Table: proto.MessageName(msg),
		Data:  b,
	})
	fmt.Printf("%x\n", b)
	return db.sendQuery(query, "")
}

func (db *DB) sendQuery(query Query, path string) (err error) {
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

func (db *DB) responseWrapper(resp *http.Response, err error) error {
	if err != nil {
		return err
	}
	b, _ := ioutil.ReadAll(resp.Body)

	// TODO: protobuf responses
	if resp.StatusCode > 299 {
		return errors.Errorf("Got error response from server: %d %s", resp.StatusCode, (b))
	}
	return nil
}
