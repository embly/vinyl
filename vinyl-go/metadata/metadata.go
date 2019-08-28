package metadata


import (
	vinyl "github.com/embly/vinyl/vinyl-go"
)

// Table is...
func Table(name, primaryKey string, attributes ...Idx) vinyl.Table {
	table := vinyl.Table{
		Name: name,
		FieldOptions: map[string]*vinyl.FieldOptions{
			primaryKey: &vinyl.FieldOptions{
				PrimaryKey: true,
			},
		},
	}

	for _, attr := range attributes {
		v := table.FieldOptions[attr.field];
		if v == nil {
			v = &vinyl.FieldOptions{}
		}
		v.Index = &vinyl.FieldOptions_IndexOption{
			Type: "value",
			Unique: attr.unique,
		}
	}
	return table
}

// Idx holds the value of an index
type Idx struct {
	field string
	unique bool
}
// Index is just an index on a field, this will likely go away as it needs to support much more complexity
func Index(field string) Idx {
	return Idx{
		field: field,
	}
}