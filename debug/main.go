package main

import (
    "github.com/apple/foundationdb/bindings/go/src/fdb"
    "log"
    "fmt"
)


func main() {
	fdb.MustAPIVersion(620)
	db := fdb.MustOpenDefault()

	ret, err := db.Transact(func(tr fdb.Transaction) (interface{}, error) {
		rng := fdb.SelectorRange{
			Begin: fdb.FirstGreaterOrEqual(fdb.Key("")),
			End:   fdb.FirstGreaterOrEqual(fdb.Key([]byte{0xff})),
		}
		return tr.GetRange(&rng, fdb.RangeOptions{}).GetSliceOrPanic(), nil
        // db.Transact automatically commits (and if necessary,
        // retries) the transaction
    })
    if err != nil {
        log.Fatalf("Unable to perform FDB transaction (%v)", err)
	}
	fmt.Println(ret)
}
