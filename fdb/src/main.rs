use fdb::Record;
use fdb_derive;
use fdb_derive::Record;
use std::time::SystemTime;

#[derive(Record)]
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
    id: i64,
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
            id: 0,
            group: String::from("staff"),
            email: String::from("max@max.com"),
            name: String::from("ok"),
            age: 12,
        }
    }
}

fn main() {
    // Event::hello_macro();
    let mut user = User::new();
    user.save();
    println!("{:?}", user.id);
}
