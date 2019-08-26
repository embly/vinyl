use fdb::Record;
use fdb_derive;
use fdb_derive::Record;
use serde::{Deserialize, Serialize};
use std::time::SystemTime;
use uuid::Uuid;

#[derive(Record, Deserialize, Serialize)]
struct Event {
    #[fdb(id)]
    id: i32,
    duration_minutes: i32,
    created_at: SystemTime,
    updated_at: SystemTime,
    name: String,
    rrule: Option<String>,
}

#[derive(Record)]
struct User {
    id: uuid::Uuid,
    #[fdb(index)]
    group: String,
    #[fdb(unique)]
    email: String,
    name: String,
    #[fdb(index)]
    age: i32,
}

impl User {
    fn new() -> Self {
        Self {
            id: uuid::Uuid::new_v4(),
            group: String::from("staff"),
            email: String::from("max@max.com"),
            name: String::from("ok"),
            age: 12,
        }
    }
}



fn handle(f: std::fs::File) {
    let mut msg = vec![];
    f.read_to_end(&msg);
    println!(msg);
}

fn main() {
    let mut user = User::new();
    user.save();
    println!("{:?}", user.id);
}

#[derive(Serialize, Deserialize)]
struct Msg {
    envelope: String,
    body: Vec<u8>,
    tag: String,
}

// fn go(msg: Msg) {
//     msg.reply(Response);

//     wasabi.spawn("db/query", Msg { tag: "hi" });

//     match wasabi.receive() {
//         Msg { tag: "hi", .. } => {}
//         _ => {}
//     };
// }
