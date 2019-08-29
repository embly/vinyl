package main

import (
	"log"

	vinyl "github.com/embly/vinyl/vinyl-go"
	md "github.com/embly/vinyl/vinyl-go/metadata"
)

func main() {
	c := vinyl.NewClient()
	if err := c.Connect("maxm", "hihihi", "foo"); err != nil {
		log.Fatal(err)
	}

	c.AddMetadata(
		md.Table("Query", "id", md.Index("email")),
	)
	// c.Get("http://localhost:8090/start")
	// c.Get("http://localhost:8090")

}
