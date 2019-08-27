package vinyl

import (
	"bytes"
	"compress/gzip"
	"crypto/tls"
	"fmt"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"net/http/cookiejar"

	proto "github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
	"golang.org/x/net/http2"
	"golang.org/x/net/publicsuffix"
)

type Client struct {
	httpClient *http.Client
}

func NewClient() *Client {

	jar, err := cookiejar.New(&cookiejar.Options{PublicSuffixList: publicsuffix.List})
	if err != nil {
		log.Fatal(err)
	}
	return &Client{
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

func (c *Client) Connect(username, password, keyspace string) (err error) {
	query := Query{
		Username: username,
		Password: password,
		Keyspace: keyspace,
	}
	query.FileDescriptor, _ = query.Descriptor()
	zr, err := gzip.NewReader(bytes.NewBuffer(query.FileDescriptor))
	if err != nil {
		log.Fatal(err)
	}
	b, err := ioutil.ReadAll(zr)
	if err != nil {
		return err
	}
	zr.Close()
	query.FileDescriptor = b
	fmt.Println(string(b))
	b, err = proto.Marshal(&query)
	if err != nil {
		return
	}
	return c.responseWrapper(c.httpClient.Post("http://localhost:8090/start", "application/protobuf", bytes.NewBuffer(b)))
}

func (c *Client) responseWrapper(resp *http.Response, err error) error {
	if err != nil {
		return err
	}
	b, _ := ioutil.ReadAll(resp.Body)

	// TODO: protobuf responses
	if resp.StatusCode > 299 {
		return errors.Errorf("Got error response from serve: %d %s", resp.StatusCode, (b))
	}
	return nil
}

func (c *Client) Get(url string) {
	resp, err := c.httpClient.Get(url)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(resp)
	b, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(string(b))
}

func (c *Client) AddMetadata(tables ...metadata.Table) {
	return
}
