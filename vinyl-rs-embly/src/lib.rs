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
use embly::spawn_function;
use embly::Conn;
pub use embly::Waitable;
use failure::{err_msg, Error};
use protobuf::{parse_from_bytes, Message};
use std::future::Future;
use std::io::Read;
use std::io::Write;
use std::pin::Pin;
use std::str;
use std::task::{Context, Poll};
use vinyl_core::proto::transport::{Request, Response as ProtoResponse};
pub use vinyl_core::query;
pub use vinyl_core::DefaultValue;
pub use vinyl_core::ToValue;

/// the db
pub struct DB {
    name: String,
    session_token: String,
}

// struct Response<T> {
//     conn: Conn,
//     value: T,
// }

// impl<Y> Future for Response<Vec<Y>>
// where
//     Y: Message,
// {
//     type Output = Result<Vec<Y>, Error>;
//     fn poll(mut self: Pin<&mut Self>, _cx: &mut Context) -> Poll<Self::Output> {
//         let poll = Pin::new(&mut self.conn).poll(_cx);
//         if let Poll::Ready(_) = poll {
//             // let mut buffer = Vec::new();
//             // self.conn.read_to_end(&mut buffer).unwrap();
//             // let mut response: ProtoResponse = parse_from_bytes(&buffer)?;
//             // let err = response.take_error();
//             // let mut v: Vec<Y> = Vec::new();
//             // for record in response.get_records().iter() {
//             //     v.push(parse_from_bytes(record)?);
//             // }
//             let mut v: Vec<Y> = Vec::new();
//             Poll::Ready(Ok(v))
//         } else {
//             Poll::Pending
//         }
//     }
// }

impl DB {
    /// make a new one
    pub async fn new(name: &str) -> Result<Self, Error> {
        let mut conn = spawn_function(&format!("embly/vinyl/{}/connect", name))?;
        conn.await?;
        let mut buf = Vec::new();
        conn.read_to_end(&mut buf)?;
        Ok(Self {
            name: name.to_string(),
            session_token: String::from(str::from_utf8(&buf)?),
        })
    }

    /// return records that match the provided query
    pub async fn execute_query<T: Message>(&self, q: query::Query) -> Result<Vec<T>, Error> {
        let req = vinyl_core::execute_query_request::<T>(q);
        let conn = self.send_request(req)?;
        let response = self.await_response(conn).await?;
        let mut v: Vec<T> = Vec::new();
        for record in response.get_records().iter() {
            v.push(parse_from_bytes(record)?);
        }
        Ok(v)
    }
    fn insert_sync<T: Message>(&self, msg: T) -> Result<Conn, Error> {
        let req = vinyl_core::insert_request::<T>(msg)?;
        let conn = self.send_request(req)?;
        Ok(conn)
    }
    /// asdfasdf
    pub fn insert<T: Message>(&self, msg: T) -> impl Future<Output = Result<(), Error>> {
        let result = self.insert_sync(msg);
        async {
            match result {
                Ok(conn) => {
                    let _response = await_response(conn.clone()).await?;
                    Ok(())
                }
                Err(err) => {
                    return Err(err);
                }
            }
        }
    }
    /// asdf
    pub fn insert_again<T: Message>(&self, msg: T) -> impl Future<Output = Result<(), Error>> {
        let req = vinyl_core::insert_request::<T>(msg).unwrap();
        let conn = self.send_request(req).unwrap();
        async move {
            let _response = await_response(conn).await?;
            Ok(())
        }
        // TODO: return any errors
    }
    // pub fn insert_again<T: Message>(
    //     &self,
    //     msg: T,
    // ) -> Result<impl Future<Output = Result<(), Error>>, Error> {
    //     let req = vinyl_core::insert_request::<T>(msg)?;
    //     let conn = self.send_request(req)?;
    //     Ok(async move {
    //         let _response = self.await_response(conn).await?;
    //         Ok(())
    //     })
    //     // TODO: return any errors
    // }
    /// asdf
    // pub async fn insert<T: Message>(&self, msg: T) -> Result<(), Error> {
    //     let req = vinyl_core::insert_request::<T>(msg)?;
    //     let conn = self.send_request(req)?;
    //     let _response = self.await_response(conn).await?;
    //     Ok(())
    //     // TODO: return any errors
    // }

    /// asdf
    pub async fn load_record<T: Message, K: ToValue>(&self, pk: K) -> Result<T, Error> {
        let req = vinyl_core::load_record::<T, K>(pk);
        let conn = self.send_request(req)?;
        let resp = self.await_response(conn).await?;
        let record = match resp.get_records().first() {
            Some(record) => record,
            None => return Err(err_msg("no record found")),
        };
        Ok(parse_from_bytes(record)?)
    }

    /// delete records that match the provided query
    pub async fn delete_record<T: Message, K: ToValue>(&self, pk: K) -> Result<(), Error> {
        let req = vinyl_core::delete_record::<T, K>(pk);
        let conn = self.send_request(req)?;
        self.await_response(conn).await?;
        Ok(())
    }

    fn send_request(&self, mut req: Request) -> Result<Conn, Error> {
        let mut conn = spawn_function(&format!("embly/vinyl/{}/request", self.name))?;
        req.set_token(self.session_token.clone());
        conn.write_all(&req.write_to_bytes()?)?;
        Ok(conn)
    }
    async fn await_response(&self, mut conn: Conn) -> Result<ProtoResponse, Error> {
        conn.await?;
        let mut buffer = Vec::new();
        conn.read_to_end(&mut buffer)?;
        let mut response: ProtoResponse = parse_from_bytes(&buffer)?;
        let err = response.take_error();
        if !err.is_empty() {
            Err(err_msg(err))
        } else {
            Ok(response)
        }
    }
}

async fn await_response(mut conn: Conn) -> Result<ProtoResponse, Error> {
    conn.await?;
    let mut buffer = Vec::new();
    conn.read_to_end(&mut buffer)?;
    let mut response: ProtoResponse = parse_from_bytes(&buffer)?;
    let err = response.take_error();
    if !err.is_empty() {
        Err(err_msg(err))
    } else {
        Ok(response)
    }
}
