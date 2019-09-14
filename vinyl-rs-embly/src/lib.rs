//! Vinyl is
//!
//!
//! ```no_run
//! use failure::Error;
//! use embly::prelude::*;
//! use vinyl_embly::query::field;
//! use vinyl_embly::DB;
//!
//! use vinyl_core::proto::example::{Color, Flower, Order};
//!
//! fn main() -> Result<(), Error> {
//!     let db = DB::new("flowers")?;
//!
//!     let mut order = Order::new();
//!     order.order_id = 2;
//!     order.price = 20;
//!
//!     let mut flower = Flower::new();
//!     flower.field_type = String::from("ROSE");
//!     flower.color = Color::RED;
//!
//!     order.set_flower(flower);
//!     db.insert(order)?.wait()?;
//!
//!     let orders: Vec<Order> = db
//!         .execute_query(
//!             field("price").less_than(50) &
//!             field("flower").matches(field("type").equals("ROSE")),
//!         )?
//!         .wait()?;
//!
//!     db.delete_record::<Order, i32>(2)?.wait()?;
//!
//!     Ok(())
//! }
//!```

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

pub use vinyl_core::query;
pub use vinyl_core::DefaultValue;
pub use vinyl_core::ToValue;

use embly::{spawn_function, Conn, Waitable};
use failure::{err_msg, Error};
use protobuf::{parse_from_bytes, Message};
use std::io::Read;
use std::io::Write;
use std::marker::PhantomData;
use std::str;
use vinyl_core::proto::transport::{Request, Response};

fn as_u32_le(array: &[u8]) -> u32 {
    u32::from(array[0])
        | (u32::from(array[1]) << 8)
        | (u32::from(array[2]) << 16)
        | (u32::from(array[3]) << 24)
}

struct ProtoResponseWaitable {
    conn: Conn,
}

impl Waitable for ProtoResponseWaitable {
    type Output = Result<Response, Error>;

    fn id(&self) -> i32 {
        self.conn.id()
    }
    fn fetch_result(&mut self) -> Result<Response, Error> {
        let mut size_bytes: [u8; 4] = [0; 4];
        self.conn.read_exact(&mut size_bytes)?;
        let size = as_u32_le(&size_bytes) as usize;
        let mut read = 0;
        let mut msg_bytes = vec![0; size];
        loop {
            let ln = self.conn.read(&mut msg_bytes[read..])?;
            read += ln;
            println!(
                "reading msg {:?}",
                (ln, msg_bytes[read..].len(), read, size)
            );
            if ln == 0 || read == size {
                break;
            }
        }
        let mut response: Response = parse_from_bytes(&msg_bytes)?;
        // response
        let err = response.take_error();
        if !err.is_empty() {
            Err(err_msg(err))
        } else {
            Ok(response)
        }
    }
}

/// a future that returns records
pub struct RecordsFuture<T> {
    response: ProtoResponseWaitable,
    phantom: PhantomData<T>,
}

impl<T: Message> Waitable for RecordsFuture<T> {
    type Output = Result<Vec<T>, Error>;

    fn id(&self) -> i32 {
        self.response.id()
    }

    fn fetch_result(&mut self) -> Result<Vec<T>, Error> {
        let resp = self.response.wait()?;
        let mut v: Vec<T> = Vec::new();
        for record in resp.get_records().iter() {
            v.push(parse_from_bytes(record).unwrap());
        }
        Ok(v)
    }
}

/// record future
pub struct RecordWaitable<T> {
    response: ProtoResponseWaitable,
    record: T,
}

impl<T: Message> Waitable for RecordWaitable<T> {
    type Output = Result<T, Error>;

    fn id(&self) -> i32 {
        self.response.id()
    }
    fn fetch_result(&mut self) -> Result<T, Error> {
        self.response.wait()?;
        Ok(::std::mem::replace(&mut self.record, T::new()))
    }
}

/// a future for an empty response
pub struct ResponseWaitable {
    response: ProtoResponseWaitable,
}

impl Waitable for ResponseWaitable {
    type Output = Result<(), Error>;

    fn id(&self) -> i32 {
        self.response.id()
    }
    fn fetch_result(&mut self) -> Result<(), Error> {
        self.response.wait()?;
        Ok(())
    }
}

/// the db
pub struct DB {
    name: String,
    session_token: String,
}

impl DB {
    /// make a new one
    pub fn new(name: &str) -> Result<Self, Error> {
        let mut conn = spawn_function(&format!("embly/vinyl/{}/connect", name))?;
        conn.wait()?;
        let mut buf = Vec::new();
        conn.read_to_end(&mut buf)?;
        Ok(Self {
            name: name.to_string(),
            session_token: String::from(str::from_utf8(&buf)?),
        })
    }

    /// return records that match the provided query
    pub fn execute_query<T: Message>(&self, q: query::Query) -> Result<RecordsFuture<T>, Error> {
        let req = vinyl_core::execute_query_request::<T>(q);
        let response = self.send_request(req)?;
        Ok(RecordsFuture {
            response,
            phantom: PhantomData,
        })
    }

    /// asdf
    pub fn insert<T: Message>(&self, msg: T) -> Result<RecordWaitable<T>, Error> {
        let (msg, req) = vinyl_core::insert_request::<T>(msg)?;
        let response = self.send_request(req)?;
        Ok(RecordWaitable {
            response,
            record: msg,
        })
    }

    /// delete records that match the provided query
    pub fn delete_record<T: protobuf::Message, K: ToValue>(
        &self,
        pk: K,
    ) -> Result<ResponseWaitable, Error> {
        let req = vinyl_core::delete_record::<T, K>(pk);
        let resp = self.send_request(req)?;
        Ok(ResponseWaitable { response: resp })
    }

    fn send_request(&self, mut req: Request) -> Result<ProtoResponseWaitable, Error> {
        let mut conn = spawn_function(&format!("embly/vinyl/{}", self.name))?;
        req.set_token(self.session_token.clone());
        conn.write_all(&req.write_to_bytes()?)?;
        Ok(ProtoResponseWaitable { conn: conn })
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
        assert_eq!(2 + 2, 4);
    }
}
