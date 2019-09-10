//! to value
//!

use crate::proto::transport::{Value, Value_ValueType};
/// ToValue is implemented for all rust types that can be converted to protobuf values
pub trait ToValue {
    /// outputs the appropriate protobuf record value
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