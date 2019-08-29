package metadata

type Table struct {
	Name       string
	PrimaryKey string
	Indexes []Index
}

type Index struct {
	Field  string
	Unique bool
}


type Metadata struct {
	Descriptor []byte
	Tables []Table
}

