extern crate proc_macro;

use crate::proc_macro::TokenStream;
use quote::quote;
use syn;

#[proc_macro_derive(Record, attributes(fdb))]
pub fn record_derive(input: TokenStream) -> TokenStream {
    // Construct a representation of Rust code as a syntax tree
    // that we can manipulate
    let ast = syn::parse(input).unwrap();

    // Build the trait implementation
    impl_hello_macro(&ast)
}

fn impl_hello_macro(ast: &syn::DeriveInput) -> TokenStream {
    println!("attrs len {:?}", ast.attrs.len());
    if let syn::Data::Struct(ref data_struct) = ast.data {
        if let syn::Fields::Named(ref fields_named) = data_struct.fields {
            // Iterate over the fields: `x`, `y`, ..
            for field in fields_named.named.iter() {
                // Get attributes `#[..]` on each field
                for attr in field.attrs.iter() {
                    // Parse the attribute
                    let meta = attr.interpret_meta().unwrap();
                    match meta {
                        syn::Meta::Word(ref ident) => println!("word {:?}", ident),
                        syn::Meta::List(ref list) => println!("list {:?}", list.ident),
                        _ => {}
                    }
                }
            }
        }
    }
    let name = &ast.ident;
    let gen = quote! {
        impl Record for #name {
            fn hello_macro() {
                println!("Hello, Macro! My name is {}", stringify!(#name));
            }
            fn save(&mut self) {
                self.id = 1
            }
        }
    };
    gen.into()
}

#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
        assert_eq!(2 + 2, 4);
    }
}
