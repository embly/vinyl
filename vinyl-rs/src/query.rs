//! define complex queries
//!
//! ```no_run
//! use vinyl::{Record, query};
//! use vinyl::query::field;
//! use failure::Error;
//! use protobuf::Message;
//! use vinyl::proto::example;
//! use vinyl::proto::example::Flower;
//!
//! fn main() -> Result<(), Error> {
//!     let db = vinyl::ConnectionBuilder::new(
//!         "vinyl://max:password@localhost:8090/foo",
//!         example::file_descriptor_proto().write_to_bytes().unwrap(),
//!     )
//!     .add_record(Record::new::<Flower>("order_id"))
//!     .connect()?;
//!
//!     let flowers: Vec<Flower> = db.execute_query(
//!         field("price").less_than(50) &
//!         field("flower").matches(
//!             field("type").equals("ROSE")
//!         )
//!     )?;
//!     println!("{:?}", flowers);
//!     Ok(())
//! }
//!```

use crate::proto::transport;
use crate::ToValue;
use protobuf::RepeatedField;

use std::ops;

/// Query is the basic component of all queries. Every query expression outputs a Query.
/// Query is not used directly except for in the case of `Query::not`
#[derive(Debug, PartialEq)]
pub struct Query {
    pub(crate) qc: transport::QueryComponent,
}
impl Query {
    fn add_field_value<T: ToValue>(mut self, value: T) -> Self {
        let mut field = self.qc.take_field();
        field.set_value(value.to_value());
        self.qc.set_field(field);
        self
    }
    fn merge(self, other: Self, merge_type: transport::QueryComponent_ComponentType) -> Self {
        let mut qc = transport::QueryComponent::new();
        qc.component_type = merge_type;
        qc.children = RepeatedField::new();
        qc.children.push(self.qc);
        qc.children.push(other.qc);
        Self { qc }
    }
    /// Check that a set of components all evaluate to true for a given record.
    /// ```rust
    /// use vinyl::query::field;
    ///
    /// field("price")
    ///     .equals(4.3)
    ///     .and(field("type").equals("rose"));
    /// ```
    pub fn and(self, other: Self) -> Self {
        self.merge(other, transport::QueryComponent_ComponentType::AND)
    }
    /// Check that any of a set of components evaluate to true for a given record
    pub fn or(self, other: Self) -> Self {
        self.merge(other, transport::QueryComponent_ComponentType::OR)
    }
    /// Negate a component test
    /// ```rust
    /// use vinyl::query::{field, Query};
    ///
    /// let query = Query::not(
    ///     field("price").equals(4.3),
    /// );
    ///```
    pub fn not(other: Self) -> Self {
        let mut qc = transport::QueryComponent::new();
        qc.component_type = transport::QueryComponent_ComponentType::NOT;
        qc.set_child(other.qc);
        Self { qc }
    }
}

impl ops::BitAnd<Query> for Query {
    type Output = Self;

    fn bitand(self, other: Self) -> Self {
        self.and(other)
    }
}

/// Context for asserting about a field value
pub struct Field {
    name: String,
}

impl Field {
    fn field_to_query(&self, component_type: transport::Field_ComponentType) -> Query {
        let mut qc = transport::QueryComponent::new();
        qc.component_type = transport::QueryComponent_ComponentType::FIELD;
        let mut field = transport::Field::new();
        field.set_name(self.name.clone());
        field.set_component_type(component_type);
        qc.set_field(field);
        Query { qc }
    }
    /// Checks if the field has a value less than the given comparand
    pub fn less_than<T: ToValue>(&self, value: T) -> Query {
        self.field_to_query(transport::Field_ComponentType::LESS_THAN)
            .add_field_value(value)
    }
    /// Check if the field has a value greater than the given comparand
    pub fn greater_than<T: ToValue>(&self, value: T) -> Query {
        self.field_to_query(transport::Field_ComponentType::GREATER_THAN)
            .add_field_value(value)
    }
    /// Check if the field is equal to the given value
    pub fn equals<T: ToValue>(&self, value: T) -> Query {
        self.field_to_query(transport::Field_ComponentType::EQUALS)
            .add_field_value(value)
    }
    /// Matches allows comparison of nested field values
    pub fn matches(&self, query: Query) -> Query {
        let mut q = self.field_to_query(transport::Field_ComponentType::MATCHES);
        let field = q.qc.mut_field();
        field.set_matches(query.qc);
        q
    }
}

/// start the construction of a field value
pub fn field(name: &str) -> Field {
    Field {
        name: name.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use transport::{Field_ComponentType, QueryComponent_ComponentType};

    #[test]
    fn complex() {
        let query = Query::not(
            field("price")
                .equals(4.3)
                .and(field("price").equals(70).or(field("price").equals(20))),
        );
        let _ = query;
    }

    #[test]
    fn basic_and_and_matches() {
        let fifty: usize = 50;
        let query =
            field("price").less_than(fifty) & field("flower").matches(field("type").equals("ROSE"));
        let qc = query.qc;
        assert_eq!(QueryComponent_ComponentType::AND, qc.component_type);
        assert_eq!("price", qc.get_children()[0].get_field().name);
        assert_eq!(
            Field_ComponentType::LESS_THAN,
            qc.get_children()[0].get_field().component_type,
        );
        assert_eq!(50, qc.get_children()[0].get_field().get_value().int64);
        assert_eq!("flower", qc.get_children()[1].get_field().get_name());
        assert_eq!(
            Field_ComponentType::MATCHES,
            qc.get_children()[1].get_field().component_type
        );
        assert_eq!(
            "type",
            qc.get_children()[1]
                .get_field()
                .get_matches()
                .get_field()
                .name
        );
        assert_eq!(
            Field_ComponentType::EQUALS,
            qc.get_children()[1]
                .get_field()
                .get_matches()
                .get_field()
                .component_type
        );
        assert_eq!(
            "ROSE",
            qc.get_children()[1]
                .get_field()
                .get_matches()
                .get_field()
                .get_value()
                .string
        );
    }

    #[test]
    fn basic_field() {
        let fifty: usize = 50;
        let query = field("price").less_than(fifty);
        assert_eq!(query.qc.get_field().name, "price");
        assert_eq!(query.qc.get_field().get_value().int64, 50);
    }
}
