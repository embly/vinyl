package descriptor

import (
	"bytes"
	"compress/gzip"
	fmt "fmt"
	"io/ioutil"

	proto "github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
)

func ptrString(val string) *string {
	return &val
}
func ptrInt32(val int32) *int32 {
	return &val
}

// AddRecordTypeUnion takes a compressed protobuf descriptor and adds the RecordTypeUnion
func AddRecordTypeUnion(descriptorBytes []byte, tables []string) (out []byte, err error) {
	zr, err := gzip.NewReader(bytes.NewBuffer(descriptorBytes))
	if err != nil {
		return
	}
	b, err := ioutil.ReadAll(zr)
	if err != nil {
		return
	}
	zr.Close()
	fdp := FileDescriptorProto{}
	if err = proto.Unmarshal(b, &fdp); err != nil {
		err = errors.Wrap(err, "error unmarshaling the descriptor, is it the right format?")
		return
	}
	descriptor := DescriptorProto{
		Name: ptrString("RecordTypeUnion"),
	}

	for i, table := range tables {
		field := FieldDescriptorProto{
			Name:     ptrString("_" + table),
			Number:   ptrInt32(int32(i + 1)),
			Label:    FieldDescriptorProto_LABEL_OPTIONAL.Enum(),
			Type:     FieldDescriptorProto_TYPE_MESSAGE.Enum(),
			TypeName: ptrString(fmt.Sprintf(".%s.%s", *fdp.Package, table)),
			JsonName: &table,
		}
		descriptor.Field = append(descriptor.Field, &field)
	}

	// TODO: check if it is already part of the proto definition?
	// ...might actually be fine if it's duplicated
	fdp.MessageType = append(fdp.MessageType, &descriptor)

	return proto.Marshal(&fdp)
}
