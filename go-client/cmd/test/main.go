package main

import (
	"log"

	vinyl "github.com/embly/vinyl/go-client"
	md "github.com/embly/vinyl/go-client/metadata"

)

func main() {
	c := vinyl.NewClient()
	if err := c.Connect("maxm", "hihihi", "foo"); err != nil {
		log.Fatal(err)
	}

	c.AddMetadata(
		qm.Table("User", "id", qm.Index("email")),
		qm.Table("User", "id", qm.Index("email")),
	)
	// c.Get("http://localhost:8090/start")
	// c.Get("http://localhost:8090")

}
