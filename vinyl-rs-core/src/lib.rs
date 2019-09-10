//! Vinyl is
//!

#![deny(
    missing_docs,
    trivial_numeric_casts,
    unstable_features,
    unused_extern_crates,
    unused_features
)]
#![warn(unused_import_braces, unused_parens)]
#![cfg_attr(feature = "clippy", plugin(clippy(conf_file = "../../clippy.toml")))]
#![cfg_attr(
    feature = "cargo-clippy",
    allow(clippy::new_without_default, clippy::new_without_default)
)]
#![cfg_attr(
    feature = "cargo-clippy",
    warn(
        clippy::float_arithmetic,
        clippy::mut_mut,
        clippy::nonminimal_bool,
        clippy::option_map_unwrap_or,
        clippy::option_map_unwrap_or_else,
        clippy::unicode_not_nfc,
        clippy::use_self
    )
)]

pub mod proto;

pub mod query;
mod to_value;

use failure::Error;
use proto::transport::{
    FieldOptions, FieldOptions_DefaultValue, FieldOptions_IndexOption, Insert, LoginRequest, Query,
    Query_QueryType, Record as ProtoRecord, RecordQuery, Request,
};
use protobuf::descriptor::{DescriptorProto, FieldDescriptorProto, FileDescriptorProto};
use protobuf::{parse_from_bytes, Message, RepeatedField};
use std::collections::HashMap;

pub use to_value::ToValue;

/// construct an insertion request from a message
pub fn insert_request<T: Message>(msg: T) -> Result<(T, Request), Error> {
    let mut req = Request::new();
    let mut insertions: RepeatedField<Insert> = RepeatedField::new();
    let mut insert = Insert::new();
    insert.set_record(msg.descriptor().name().to_string());
    insert.set_data(msg.write_to_bytes()?);
    insertions.push(insert);
    req.set_insertions(insertions);
    Ok((msg, req))
}

/// construct a request for a query execution
pub fn execute_query_request<T: Message>(q: query::Query) -> Request {
    let mut query = Query::new();
    query.set_record_type(T::default_instance().descriptor().name().to_string());
    query.set_query_type(Query_QueryType::RECORD_QUERY);
    let mut record_query = RecordQuery::new();
    record_query.set_filter(q.qc);
    query.set_record_query(record_query);
    let mut req = Request::new();
    req.set_query(query);
    req
}

/// construct a deletion request
pub fn delete_record<T: Message, K: ToValue>(pk: K) -> Request {
    let mut query = Query::new();
    query.set_query_type(Query_QueryType::DELETE_RECORD);
    query.set_primary_key(pk.to_value());
    query.set_record_type(T::default_instance().descriptor().name().to_string());
    let mut req = Request::new();
    req.set_query(query);
    req
}

/// TKTKTK
pub fn construct_login_request(
    descriptor_bytes: Vec<u8>,
    records: Vec<Record>,
    username: &str,
    password: &str,
    keyspace: &str,
) -> Result<LoginRequest, Error> {
    let mut fd_proto: FileDescriptorProto = parse_from_bytes(&descriptor_bytes)?;
    let mut descriptor = DescriptorProto::new();
    descriptor.set_name("RecordTypeUnion".to_string());

    let mut fields: RepeatedField<FieldDescriptorProto> = RepeatedField::new();
    let package_name = if fd_proto.get_package().is_empty() {
        String::from("")
    } else {
        format!(".{}", fd_proto.get_package())
    };
    for (i, record) in records.iter().enumerate() {
        let mut field = FieldDescriptorProto::new();
        field.set_name(format!("_{}", record.name));
        field.set_number(i as i32 + 1);
        field.set_label(protobuf::descriptor::FieldDescriptorProto_Label::LABEL_OPTIONAL);
        field.set_field_type(protobuf::descriptor::FieldDescriptorProto_Type::TYPE_MESSAGE);
        field.set_type_name(format!("{}.{}", package_name, record.name));
        field.set_json_name(record.name.to_string());
        fields.push(field);
    }
    descriptor.set_field(fields);

    let mut message_type = fd_proto.take_message_type();
    message_type.push(descriptor);
    fd_proto.set_message_type(message_type);

    let mut proto_records: RepeatedField<ProtoRecord> = RepeatedField::new();
    for record in records {
        let mut record_proto = ProtoRecord::new();
        record_proto.set_name(record.name);
        let mut fo_pk = FieldOptions::new();
        fo_pk.set_primary_key(true);
        let mut fo_map: HashMap<String, FieldOptions> =
            [(record.primary_key, fo_pk)].iter().cloned().collect();
        for index in record.indexes {
            let mut fo = fo_map.remove(&index.name).unwrap_or_default();
            let mut fo_io = FieldOptions_IndexOption::new();
            fo_io.set_field_type("value".to_string());
            fo_io.set_unique(index.unique);
            fo.set_index(fo_io);
            fo_map.insert(index.name, fo);
        }
        println!("{:?}", fo_map);
        record_proto.set_field_options(fo_map);
        proto_records.push(record_proto);
    }
    let mut login_request = LoginRequest::new();
    login_request.set_records(proto_records);
    login_request.file_descriptor = fd_proto.write_to_bytes()?;
    login_request.keyspace = keyspace.to_string();
    login_request.username = username.to_string();
    login_request.password = password.to_string();

    Ok(login_request)
}

// TODO: add our own print fn
/// A record index
#[derive(Debug)]
pub struct Index {
    name: String,
    unique: bool,
}

impl Index {
    /// create a new index with a record field name
    pub fn new(name: &str) -> Self {
        Self {
            name: name.to_string(),
            unique: false,
        }
    }
    /// make this index a unique index
    pub fn unique(mut self) -> Self {
        self.unique = true;
        self
    }
}

// TODO: add our own print fn
/// defines various defalt and on-update field values
#[derive(Debug)]
pub struct DefaultValue {
    name: String,
    default_value: FieldOptions_DefaultValue,
}

/// default value types
pub enum DefaultValueType {
    /// Updates the field with the current time on creation. Field type must be i64
    TimeNow,
    /// Updates the field with the current time whenever the value is updates. Field type must be i64
    TimeNowOnUpdate,
    /// Sets a byte field to a random UUID when it is empty. A good choice for a primary key field
    UUID,
}

impl DefaultValue {
    /// create a new DefaultValue for a record
    pub fn new(name: &str, value_type: DefaultValueType) -> Self {
        Self {
            name: name.to_string(),
            default_value: match value_type {
                DefaultValueType::TimeNow => FieldOptions_DefaultValue::NOW,
                DefaultValueType::UUID => FieldOptions_DefaultValue::UUID,
                DefaultValueType::TimeNowOnUpdate => FieldOptions_DefaultValue::NOW_ON_UPDATE,
            },
        }
    }
}

// TODO: add our own print fn
/// holds metadata for a given record. Records are roughly equivalent to an SQL table
#[derive(Debug)]
pub struct Record {
    indexes: Vec<Index>,
    default_values: Vec<DefaultValue>,
    name: String,
    primary_key: String,
}

impl Record {
    /// create a new record. records must have a primary key. pass a valid field name as the first value
    pub fn new<T: protobuf::Message>(primary_key: &str) -> Self {
        Self {
            name: T::new().descriptor().name().to_string(),
            indexes: Vec::new(),
            default_values: Vec::new(),
            primary_key: primary_key.to_string(),
        }
    }
    /// add an index to this record
    pub fn add_index(mut self, idx: Index) -> Self {
        self.indexes.push(idx);
        self
    }
    /// add a default value to this record
    pub fn add_default_value(mut self, dv: DefaultValue) -> Self {
        self.default_values.push(dv);
        self
    }
}
