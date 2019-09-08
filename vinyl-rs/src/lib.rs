#![deny(
    // missing_docs,
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

pub mod query;
mod transport;

#[macro_use]
extern crate failure;

use failure::Error;
use grpc::ClientStubExt;
use protobuf;
use protobuf::descriptor::{DescriptorProto, FieldDescriptorProto, FileDescriptorProto};
use protobuf::{parse_from_bytes, Message, RepeatedField};
use std::collections::HashMap;
use transport::transport::{
    Insert, Query, Query_QueryType, Request, Response, Value, Value_ValueType,
};
use transport::transport_grpc::{Vinyl, VinylClient};
use url::Url;

pub fn init() {
    println!("done")
}

pub fn query<T: protobuf::Message>(msg: T) -> bool {
    msg.is_initialized()
}

pub struct DB {
    client: VinylClient,
    token: String,
}

pub trait ToValue {
    fn to_value(self) -> Value;
}

macro_rules! impl_value {
    ($ty:ident, $field:ident, $value_type:ident) => {
        impl ToValue for $ty {
            fn to_value(self) -> Value {
                let mut v = Value::new();
                v.$field(self);
                v.set_value_type(Value_ValueType::$value_type);
                v
            }
        }
    };
}

// special case
impl ToValue for usize {
    fn to_value(self) -> Value {
        let mut v = Value::new();
        v.int64 = self as i64;
        v.set_value_type(Value_ValueType::INT64);
        v
    }
}

impl ToValue for Vec<u8> {
    fn to_value(self) -> Value {
        let mut v = Value::new();
        v.bytes = self;
        v.set_value_type(Value_ValueType::BYTES);
        v
    }
}

impl ToValue for &str {
    fn to_value(self) -> Value {
        let mut v = Value::new();
        v.string = self.to_string();
        v.set_value_type(Value_ValueType::STRING);
        v
    }
}

impl_value!(f64, set_double, DOUBLE);
impl_value!(f32, set_float, FLOAT);
impl_value!(i32, set_int32, INT32);
impl_value!(bool, set_bool, BOOL);
impl_value!(String, set_string, STRING);

impl DB {
    pub fn insert<T: protobuf::Message>(&self, msg: T) -> Result<(), Error> {
        let mut req = Request::new();
        let mut insertions: RepeatedField<Insert> = RepeatedField::new();
        let mut insert = Insert::new();
        insert.set_record(msg.descriptor().name().to_string());
        insert.set_data(msg.write_to_bytes()?);
        insertions.push(insert);
        req.set_insertions(insertions);
        req.set_token(self.token.to_string());
        self.send_request(req)?;
        Ok(())
    }

    pub fn execute_query<T: protobuf::Message>(&self, q: query::Query) -> Result<Vec<T>, Error> {
        let mut query = Query::new();

        let tmp = T::new();
        query.set_record_type(tmp.descriptor().name().to_string());

        query.set_query_type(Query_QueryType::RECORD_QUERY);
        let mut record_query = transport::transport::RecordQuery::new();
        record_query.set_filter(q.qc);
        query.set_record_query(record_query);
        let resp = self.send_query(query)?;
        let mut v = Vec::new();
        for record in resp.get_records().iter() {
            v.push(parse_from_bytes(record).unwrap());
        }
        Ok(v)
    }

    pub fn delete_record<T: protobuf::Message, K: ToValue>(&self, pk: K) -> Result<(), Error> {
        let mut query = Query::new();
        query.set_query_type(Query_QueryType::DELETE_RECORD);
        query.set_primary_key(pk.to_value());
        query.set_record_type(T::new().descriptor().name().to_string());
        self.send_query(query)?;
        Ok(())
    }

    fn send_query(&self, query: Query) -> Result<Response, Error> {
        let mut req = Request::new();
        req.set_query(query);
        req.set_token(self.token.to_string());
        self.send_request(req)
    }

    fn send_request(&self, req: Request) -> Result<Response, Error> {
        let (_, resp, _) = self.client.query(grpc::RequestOptions::new(), req).wait()?;
        if !resp.error.is_empty() {
            Err(format_err!("{}", resp.error))
        } else {
            Ok(resp)
        }
    }
}
// TODO: add our own print fn
#[derive(Debug)]
pub struct Index {
    name: String,
    unique: bool,
}

impl Index {
    pub fn new(name: &str) -> Self {
        Self {
            name: name.to_string(),
            unique: false,
        }
    }
    pub fn unique(mut self) -> Self {
        self.unique = true;
        self
    }
}

// TODO: add our own print fn
#[derive(Debug)]
pub struct Record {
    indexes: Vec<Index>,
    name: String,
    primary_key: String,
}

impl Record {
    pub fn new<T: protobuf::Message>(primary_key: &str) -> Self {
        Self {
            name: T::new().descriptor().name().to_string(),
            indexes: Vec::new(),
            primary_key: primary_key.to_string(),
        }
    }
    pub fn add_index(mut self, idx: Index) -> Self {
        self.indexes.push(idx);
        self
    }
}

pub struct ConnectionBuilder {
    connection_string: String,
    descriptor_bytes: Vec<u8>,
    records: Vec<Record>,
}

impl ConnectionBuilder {
    pub fn new(connection_string: &str, descriptor_bytes: Vec<u8>) -> Self {
        Self {
            records: Vec::new(),
            descriptor_bytes,
            connection_string: connection_string.to_string(),
        }
    }
    pub fn add_record(mut self, record: Record) -> Self {
        self.records.push(record);
        self
    }

    pub fn connect(self) -> Result<DB, Error> {
        let mut fd_proto: FileDescriptorProto = parse_from_bytes(&self.descriptor_bytes)?;
        let mut descriptor = DescriptorProto::new();
        descriptor.set_name("RecordTypeUnion".to_string());

        let mut fields: RepeatedField<FieldDescriptorProto> = RepeatedField::new();

        let package_name = if fd_proto.get_package().is_empty() {
            String::from("")
        } else {
            format!(".{}", fd_proto.get_package())
        };
        for (i, record) in self.records.iter().enumerate() {
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

        let url = Url::parse(&self.connection_string)?;
        let addrs = url.socket_addrs(|| None)?;
        let addr = addrs.first().ok_or_else(|| {
            format_err!("Couldn't resolve an ip address for the provided hostname")
        })?;
        let client =
            VinylClient::new_plain(&addr.ip().to_string(), addr.port(), Default::default())?;

        let mut records: RepeatedField<transport::transport::Record> = RepeatedField::new();
        for record in self.records {
            let mut record_proto = transport::transport::Record::new();
            record_proto.set_name(record.name);
            let mut fo_pk = transport::transport::FieldOptions::new();
            fo_pk.set_primary_key(true);
            let mut fo_map: HashMap<String, transport::transport::FieldOptions> =
                [(record.primary_key, fo_pk)].iter().cloned().collect();
            for index in record.indexes {
                let mut fo = fo_map.remove(&index.name).unwrap_or_default();
                let mut fo_io = transport::transport::FieldOptions_IndexOption::new();
                fo_io.set_field_type("value".to_string());
                fo_io.set_unique(index.unique);
                fo.set_index(fo_io);
                fo_map.insert(index.name, fo);
            }
            println!("{:?}", fo_map);
            record_proto.set_field_options(fo_map);
            records.push(record_proto);
        }
        let mut login_request = transport::transport::LoginRequest::new();
        login_request.set_records(records);
        login_request.file_descriptor = fd_proto.write_to_bytes()?;
        login_request.keyspace = url.path().to_string();
        login_request.username = url.username().to_string();
        login_request.password = url
            .password()
            .ok_or_else(|| format_err!("No password provided"))?
            .to_string();
        let resp = client.login(grpc::RequestOptions::new(), login_request);
        let (_, login_response, _) = resp.wait()?;

        Ok(DB {
            client,
            token: login_response.token,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn it_works() {
        assert_eq!(2 + 2, 4);
    }
}
