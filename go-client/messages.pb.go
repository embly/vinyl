// Code generated by protoc-gen-go. DO NOT EDIT.
// source: messages.proto

package vinyl

import (
	fmt "fmt"
	proto "github.com/golang/protobuf/proto"
	math "math"
)

// Reference imports to suppress errors if they are not otherwise used.
var _ = proto.Marshal
var _ = fmt.Errorf
var _ = math.Inf

// This is a compile-time assertion to ensure that this generated file
// is compatible with the proto package it is being compiled against.
// A compilation error at this line likely means your copy of the
// proto package needs to be updated.
const _ = proto.ProtoPackageIsVersion3 // please upgrade the proto package

type Query struct {
	Username             string   `protobuf:"bytes,1,opt,name=username,proto3" json:"username,omitempty"`
	Password             string   `protobuf:"bytes,2,opt,name=password,proto3" json:"password,omitempty"`
	Keyspace             string   `protobuf:"bytes,3,opt,name=keyspace,proto3" json:"keyspace,omitempty"`
	FileDescriptor       []byte   `protobuf:"bytes,4,opt,name=file_descriptor,json=fileDescriptor,proto3" json:"file_descriptor,omitempty"`
	Tables               []*Table `protobuf:"bytes,5,rep,name=tables,proto3" json:"tables,omitempty"`
	XXX_NoUnkeyedLiteral struct{} `json:"-"`
	XXX_unrecognized     []byte   `json:"-"`
	XXX_sizecache        int32    `json:"-"`
}

func (m *Query) Reset()         { *m = Query{} }
func (m *Query) String() string { return proto.CompactTextString(m) }
func (*Query) ProtoMessage()    {}
func (*Query) Descriptor() ([]byte, []int) {
	return fileDescriptor_4dc296cbfe5ffcd5, []int{0}
}

func (m *Query) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_Query.Unmarshal(m, b)
}
func (m *Query) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_Query.Marshal(b, m, deterministic)
}
func (m *Query) XXX_Merge(src proto.Message) {
	xxx_messageInfo_Query.Merge(m, src)
}
func (m *Query) XXX_Size() int {
	return xxx_messageInfo_Query.Size(m)
}
func (m *Query) XXX_DiscardUnknown() {
	xxx_messageInfo_Query.DiscardUnknown(m)
}

var xxx_messageInfo_Query proto.InternalMessageInfo

func (m *Query) GetUsername() string {
	if m != nil {
		return m.Username
	}
	return ""
}

func (m *Query) GetPassword() string {
	if m != nil {
		return m.Password
	}
	return ""
}

func (m *Query) GetKeyspace() string {
	if m != nil {
		return m.Keyspace
	}
	return ""
}

func (m *Query) GetFileDescriptor() []byte {
	if m != nil {
		return m.FileDescriptor
	}
	return nil
}

func (m *Query) GetTables() []*Table {
	if m != nil {
		return m.Tables
	}
	return nil
}

type RecordTypeUnion struct {
	XQuery               *Query   `protobuf:"bytes,1,opt,name=_Query,json=Query,proto3" json:"_Query,omitempty"`
	XXX_NoUnkeyedLiteral struct{} `json:"-"`
	XXX_unrecognized     []byte   `json:"-"`
	XXX_sizecache        int32    `json:"-"`
}

func (m *RecordTypeUnion) Reset()         { *m = RecordTypeUnion{} }
func (m *RecordTypeUnion) String() string { return proto.CompactTextString(m) }
func (*RecordTypeUnion) ProtoMessage()    {}
func (*RecordTypeUnion) Descriptor() ([]byte, []int) {
	return fileDescriptor_4dc296cbfe5ffcd5, []int{1}
}

func (m *RecordTypeUnion) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_RecordTypeUnion.Unmarshal(m, b)
}
func (m *RecordTypeUnion) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_RecordTypeUnion.Marshal(b, m, deterministic)
}
func (m *RecordTypeUnion) XXX_Merge(src proto.Message) {
	xxx_messageInfo_RecordTypeUnion.Merge(m, src)
}
func (m *RecordTypeUnion) XXX_Size() int {
	return xxx_messageInfo_RecordTypeUnion.Size(m)
}
func (m *RecordTypeUnion) XXX_DiscardUnknown() {
	xxx_messageInfo_RecordTypeUnion.DiscardUnknown(m)
}

var xxx_messageInfo_RecordTypeUnion proto.InternalMessageInfo

func (m *RecordTypeUnion) GetXQuery() *Query {
	if m != nil {
		return m.XQuery
	}
	return nil
}

type Table struct {
	FieldOptions         map[string]*FieldOptions `protobuf:"bytes,1,rep,name=field_options,json=fieldOptions,proto3" json:"field_options,omitempty" protobuf_key:"bytes,1,opt,name=key,proto3" protobuf_val:"bytes,2,opt,name=value,proto3"`
	Name                 string                   `protobuf:"bytes,2,opt,name=name,proto3" json:"name,omitempty"`
	XXX_NoUnkeyedLiteral struct{}                 `json:"-"`
	XXX_unrecognized     []byte                   `json:"-"`
	XXX_sizecache        int32                    `json:"-"`
}

func (m *Table) Reset()         { *m = Table{} }
func (m *Table) String() string { return proto.CompactTextString(m) }
func (*Table) ProtoMessage()    {}
func (*Table) Descriptor() ([]byte, []int) {
	return fileDescriptor_4dc296cbfe5ffcd5, []int{2}
}

func (m *Table) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_Table.Unmarshal(m, b)
}
func (m *Table) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_Table.Marshal(b, m, deterministic)
}
func (m *Table) XXX_Merge(src proto.Message) {
	xxx_messageInfo_Table.Merge(m, src)
}
func (m *Table) XXX_Size() int {
	return xxx_messageInfo_Table.Size(m)
}
func (m *Table) XXX_DiscardUnknown() {
	xxx_messageInfo_Table.DiscardUnknown(m)
}

var xxx_messageInfo_Table proto.InternalMessageInfo

func (m *Table) GetFieldOptions() map[string]*FieldOptions {
	if m != nil {
		return m.FieldOptions
	}
	return nil
}

func (m *Table) GetName() string {
	if m != nil {
		return m.Name
	}
	return ""
}

// from record_metadata_options.proto
type FieldOptions struct {
	PrimaryKey           bool                      `protobuf:"varint,2,opt,name=primary_key,json=primaryKey,proto3" json:"primary_key,omitempty"`
	Index                *FieldOptions_IndexOption `protobuf:"bytes,3,opt,name=index,proto3" json:"index,omitempty"`
	XXX_NoUnkeyedLiteral struct{}                  `json:"-"`
	XXX_unrecognized     []byte                    `json:"-"`
	XXX_sizecache        int32                     `json:"-"`
}

func (m *FieldOptions) Reset()         { *m = FieldOptions{} }
func (m *FieldOptions) String() string { return proto.CompactTextString(m) }
func (*FieldOptions) ProtoMessage()    {}
func (*FieldOptions) Descriptor() ([]byte, []int) {
	return fileDescriptor_4dc296cbfe5ffcd5, []int{3}
}

func (m *FieldOptions) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_FieldOptions.Unmarshal(m, b)
}
func (m *FieldOptions) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_FieldOptions.Marshal(b, m, deterministic)
}
func (m *FieldOptions) XXX_Merge(src proto.Message) {
	xxx_messageInfo_FieldOptions.Merge(m, src)
}
func (m *FieldOptions) XXX_Size() int {
	return xxx_messageInfo_FieldOptions.Size(m)
}
func (m *FieldOptions) XXX_DiscardUnknown() {
	xxx_messageInfo_FieldOptions.DiscardUnknown(m)
}

var xxx_messageInfo_FieldOptions proto.InternalMessageInfo

func (m *FieldOptions) GetPrimaryKey() bool {
	if m != nil {
		return m.PrimaryKey
	}
	return false
}

func (m *FieldOptions) GetIndex() *FieldOptions_IndexOption {
	if m != nil {
		return m.Index
	}
	return nil
}

type FieldOptions_IndexOption struct {
	Type                 string   `protobuf:"bytes,1,opt,name=type,proto3" json:"type,omitempty"`
	Unique               bool     `protobuf:"varint,2,opt,name=unique,proto3" json:"unique,omitempty"`
	XXX_NoUnkeyedLiteral struct{} `json:"-"`
	XXX_unrecognized     []byte   `json:"-"`
	XXX_sizecache        int32    `json:"-"`
}

func (m *FieldOptions_IndexOption) Reset()         { *m = FieldOptions_IndexOption{} }
func (m *FieldOptions_IndexOption) String() string { return proto.CompactTextString(m) }
func (*FieldOptions_IndexOption) ProtoMessage()    {}
func (*FieldOptions_IndexOption) Descriptor() ([]byte, []int) {
	return fileDescriptor_4dc296cbfe5ffcd5, []int{3, 0}
}

func (m *FieldOptions_IndexOption) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_FieldOptions_IndexOption.Unmarshal(m, b)
}
func (m *FieldOptions_IndexOption) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_FieldOptions_IndexOption.Marshal(b, m, deterministic)
}
func (m *FieldOptions_IndexOption) XXX_Merge(src proto.Message) {
	xxx_messageInfo_FieldOptions_IndexOption.Merge(m, src)
}
func (m *FieldOptions_IndexOption) XXX_Size() int {
	return xxx_messageInfo_FieldOptions_IndexOption.Size(m)
}
func (m *FieldOptions_IndexOption) XXX_DiscardUnknown() {
	xxx_messageInfo_FieldOptions_IndexOption.DiscardUnknown(m)
}

var xxx_messageInfo_FieldOptions_IndexOption proto.InternalMessageInfo

func (m *FieldOptions_IndexOption) GetType() string {
	if m != nil {
		return m.Type
	}
	return ""
}

func (m *FieldOptions_IndexOption) GetUnique() bool {
	if m != nil {
		return m.Unique
	}
	return false
}

func init() {
	proto.RegisterType((*Query)(nil), "vinyl.Query")
	proto.RegisterType((*RecordTypeUnion)(nil), "vinyl.RecordTypeUnion")
	proto.RegisterType((*Table)(nil), "vinyl.Table")
	proto.RegisterMapType((map[string]*FieldOptions)(nil), "vinyl.Table.FieldOptionsEntry")
	proto.RegisterType((*FieldOptions)(nil), "vinyl.FieldOptions")
	proto.RegisterType((*FieldOptions_IndexOption)(nil), "vinyl.FieldOptions.IndexOption")
}

func init() { proto.RegisterFile("messages.proto", fileDescriptor_4dc296cbfe5ffcd5) }

var fileDescriptor_4dc296cbfe5ffcd5 = []byte{
	// 371 bytes of a gzipped FileDescriptorProto
	0x1f, 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0xff, 0x6c, 0x52, 0xcb, 0xae, 0xd3, 0x30,
	0x10, 0x95, 0xdb, 0x26, 0x2a, 0x93, 0xd0, 0x82, 0x91, 0x50, 0xd4, 0x05, 0xad, 0x02, 0x12, 0x65,
	0x93, 0x45, 0x11, 0x08, 0xd8, 0xf2, 0x90, 0x10, 0x0b, 0x84, 0x55, 0xd6, 0x51, 0xda, 0x4c, 0x91,
	0xd5, 0xd4, 0x36, 0x76, 0x52, 0xf0, 0xe7, 0xb0, 0xe5, 0x03, 0xf8, 0x3e, 0x64, 0x3b, 0xed, 0x8d,
	0x74, 0xef, 0x6e, 0xce, 0x63, 0x1e, 0x1e, 0x0f, 0xcc, 0x4e, 0x68, 0x4c, 0xf5, 0x03, 0x4d, 0xa1,
	0xb4, 0x6c, 0x25, 0x8d, 0xce, 0x5c, 0xd8, 0x26, 0xff, 0x4b, 0x20, 0xfa, 0xd6, 0xa1, 0xb6, 0x74,
	0x01, 0xd3, 0xce, 0xa0, 0x16, 0xd5, 0x09, 0x33, 0xb2, 0x22, 0xeb, 0x7b, 0xec, 0x8a, 0x9d, 0xa6,
	0x2a, 0x63, 0x7e, 0x49, 0x5d, 0x67, 0xa3, 0xa0, 0x5d, 0xb0, 0xd3, 0x8e, 0x68, 0x8d, 0xaa, 0xf6,
	0x98, 0x8d, 0x83, 0x76, 0xc1, 0xf4, 0x39, 0xcc, 0x0f, 0xbc, 0xc1, 0xb2, 0x46, 0xb3, 0xd7, 0x5c,
	0xb5, 0x52, 0x67, 0x93, 0x15, 0x59, 0xa7, 0x6c, 0xe6, 0xe8, 0x0f, 0x57, 0x96, 0x3e, 0x83, 0xb8,
	0xad, 0x76, 0x0d, 0x9a, 0x2c, 0x5a, 0x8d, 0xd7, 0xc9, 0x26, 0x2d, 0xfc, 0x78, 0xc5, 0xd6, 0x91,
	0xac, 0xd7, 0xf2, 0xd7, 0x30, 0x67, 0xb8, 0x97, 0xba, 0xde, 0x5a, 0x85, 0xdf, 0x05, 0x97, 0x82,
	0x3e, 0x85, 0xb8, 0xf4, 0xf3, 0xfb, 0x99, 0x6f, 0x12, 0x3d, 0xc7, 0xc2, 0xd3, 0xf2, 0x7f, 0x04,
	0x22, 0x5f, 0x89, 0xbe, 0x87, 0xfb, 0x07, 0x8e, 0x4d, 0x5d, 0x4a, 0xd5, 0x72, 0x29, 0x4c, 0x46,
	0x7c, 0xbb, 0x27, 0xc3, 0x76, 0xc5, 0x27, 0xe7, 0xf8, 0x1a, 0x0c, 0x1f, 0x45, 0xab, 0x2d, 0x4b,
	0x0f, 0x03, 0x8a, 0x52, 0x98, 0xf8, 0x2d, 0x85, 0x4d, 0xf8, 0x78, 0xb1, 0x85, 0x87, 0xb7, 0xd2,
	0xe8, 0x03, 0x18, 0x1f, 0xd1, 0xf6, 0xdb, 0x74, 0x21, 0x7d, 0x01, 0xd1, 0xb9, 0x6a, 0xba, 0x90,
	0x9b, 0x6c, 0x1e, 0xf5, 0x7d, 0x87, 0xa9, 0x2c, 0x38, 0xde, 0x8d, 0xde, 0x90, 0xfc, 0x0f, 0x81,
	0x74, 0xa8, 0xd1, 0x25, 0x24, 0x4a, 0xf3, 0x53, 0xa5, 0x6d, 0xe9, 0x2a, 0xbb, 0x2a, 0x53, 0x06,
	0x3d, 0xf5, 0x05, 0x2d, 0x7d, 0x05, 0x11, 0x17, 0x35, 0xfe, 0xf6, 0x5f, 0x91, 0x6c, 0x96, 0x77,
	0x34, 0x28, 0x3e, 0x3b, 0x43, 0x00, 0x2c, 0xb8, 0x17, 0x6f, 0x21, 0x19, 0xb0, 0xee, 0x85, 0xad,
	0x55, 0x97, 0x3b, 0xf0, 0x31, 0x7d, 0x0c, 0x71, 0x27, 0xf8, 0xcf, 0x7e, 0xf6, 0x29, 0xeb, 0xd1,
	0x2e, 0xf6, 0xf7, 0xf4, 0xf2, 0x7f, 0x00, 0x00, 0x00, 0xff, 0xff, 0x4d, 0x26, 0xe7, 0x5c, 0x61,
	0x02, 0x00, 0x00,
}
