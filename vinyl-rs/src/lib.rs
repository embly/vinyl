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

/// protobuf generated files
pub mod proto;

pub use vinyl_core::query;
pub use vinyl_core::ToValue;
pub use vinyl_core::{DefaultValue, Index, Record};

#[macro_use]
extern crate failure;

use failure::Error;
use grpc::ClientStubExt;
use proto::transport_grpc::{Vinyl, VinylClient};
use protobuf;
use protobuf::parse_from_bytes;
use protobuf::Message as _;
use url::Url;
use vinyl_core::proto::transport::{Request, Response};

/// An instance of the vinyl DB. Holds metadata and a connection to the database server
pub struct DB {
    client: VinylClient,
    token: String,
}

impl DB {
    /// insert a record
    pub fn insert<T: protobuf::Message>(&self, msg: T) -> Result<T, Error> {
        let (msg, req) = vinyl_core::insert_request(msg)?;
        self.send_request(req)?;
        Ok(msg)
    }
    /// return records that match the provided query
    pub fn execute_query<T: protobuf::Message>(&self, q: query::Query) -> Result<Vec<T>, Error> {
        let req = vinyl_core::execute_query_request::<T>(q);
        let resp = self.send_request(req)?;
        let mut v = Vec::new();
        for record in resp.get_records().iter() {
            v.push(parse_from_bytes(record).unwrap());
        }
        Ok(v)
    }
    /// delete records that match the provided query
    pub fn delete_record<T: protobuf::Message, K: ToValue>(&self, pk: K) -> Result<(), Error> {
        let req = vinyl_core::delete_record::<T, K>(pk);
        self.send_request(req)?;
        Ok(())
    }

    /// send a request using bytes that can be marshalled into a Request protobuf message
    pub fn send_raw_request(&self, request_bytes: Vec<u8>) -> Result<Vec<u8>, Error> {
        let request: Request = parse_from_bytes(&request_bytes)?;
        let result = self.send_request(request)?;
        // TODO: access raw grpc bytes to prevent double unmarshalling
        Ok(result.write_to_bytes()?)
    }

    fn send_request(&self, mut req: Request) -> Result<Response, Error> {
        req.set_token(self.token.to_string());
        let (_, resp, _) = self.client.query(grpc::RequestOptions::new(), req).wait()?;
        if !resp.error.is_empty() {
            Err(format_err!("{}", resp.error))
        } else {
            Ok(resp)
        }
    }
}

/// build connection details and metadata before connecting to the database server
pub struct ConnectionBuilder {
    connection_string: String,
    descriptor_bytes: Vec<u8>,
    records: Vec<Record>,
}

impl ConnectionBuilder {
    /// pass a connection string and descriptor
    pub fn new(connection_string: &str, descriptor_bytes: Vec<u8>) -> Self {
        Self {
            records: Vec::new(),
            descriptor_bytes,
            connection_string: connection_string.to_string(),
        }
    }
    /// add record data
    pub fn add_record(mut self, record: Record) -> Self {
        self.records.push(record);
        self
    }

    /// connect to the database server
    pub fn connect(self) -> Result<DB, Error> {
        let url = Url::parse(&self.connection_string)?;
        let addrs = url.socket_addrs(|| None)?;
        let addr = addrs.first().ok_or_else(|| {
            format_err!("Couldn't resolve an ip address for the provided hostname")
        })?;
        let client =
            VinylClient::new_plain(&addr.ip().to_string(), addr.port(), Default::default())?;

        let keyspace = url.path();
        let username = url.username();
        let password = url
            .password()
            .ok_or_else(|| format_err!("No password provided"))?;

        let login_request = vinyl_core::construct_login_request(
            self.descriptor_bytes,
            self.records,
            username,
            password,
            keyspace,
        )?;
        let resp = client.login(grpc::RequestOptions::new(), login_request);
        let (_, login_response, _) = resp.wait()?;

        Ok(DB {
            client,
            token: login_response.token,
        })
    }
}
