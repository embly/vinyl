#![deny(
    trivial_numeric_casts,
    unstable_features,
    unused_extern_crates,
    unused_features
)]
#![warn(unused_import_braces, unused_parens)]
#![deny(clippy::all)]

mod proto;
use failure::Error;
use proto::tables::{file_descriptor_proto, User};
use protobuf::Message;
use vinyl;
use vinyl::query;

fn main() -> Result<(), Error> {
    let db = vinyl::ConnectionBuilder::new(
        "vinyl://max:password@localhost:8090/foo",
        file_descriptor_proto().write_to_bytes().unwrap(),
    )
    .add_record(vinyl::Record::new::<User>("id").add_index(vinyl::Index::new("email").unique()))
    .connect()?;

    let mut user = User::new();
    user.set_email("max@max.com".to_string());
    user.set_id("yo_pk".to_string());
    db.insert(user)?;

    let mut user = User::new();
    user.set_email("max@max.com".to_string());
    user.set_id("yo_pk2".to_string());
    db.insert(user).expect_err("uniqueness violation");

    db.delete_record::<User, &str>("hi")
        .expect_err("shouldn't exist");

    let users: Vec<User> = db.execute_query(query::field("email").equals("max@max.com"))?;

    println!("{:?}", users);
    Ok(())
}
