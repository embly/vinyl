// This file is generated. Do not edit
// @generated

// https://github.com/Manishearth/rust-clippy/issues/702
#![allow(unknown_lints)]
#![allow(clippy::all)]

#![cfg_attr(rustfmt, rustfmt_skip)]

#![allow(box_pointers)]
#![allow(dead_code)]
#![allow(missing_docs)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![allow(non_upper_case_globals)]
#![allow(trivial_casts)]
#![allow(unsafe_code)]
#![allow(unused_imports)]
#![allow(unused_results)]


// interface

pub trait Vinyl {
    fn login(&self, o: ::grpc::RequestOptions, p: super::transport::LoginRequest) -> ::grpc::SingleResponse<super::transport::LoginResponse>;

    fn query(&self, o: ::grpc::RequestOptions, p: super::transport::Request) -> ::grpc::SingleResponse<super::transport::Response>;
}

// client

pub struct VinylClient {
    grpc_client: ::std::sync::Arc<::grpc::Client>,
    method_Login: ::std::sync::Arc<::grpc::rt::MethodDescriptor<super::transport::LoginRequest, super::transport::LoginResponse>>,
    method_Query: ::std::sync::Arc<::grpc::rt::MethodDescriptor<super::transport::Request, super::transport::Response>>,
}

impl ::grpc::ClientStub for VinylClient {
    fn with_client(grpc_client: ::std::sync::Arc<::grpc::Client>) -> Self {
        VinylClient {
            grpc_client: grpc_client,
            method_Login: ::std::sync::Arc::new(::grpc::rt::MethodDescriptor {
                name: "/vinyl.Vinyl/Login".to_string(),
                streaming: ::grpc::rt::GrpcStreaming::Unary,
                req_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
                resp_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
            }),
            method_Query: ::std::sync::Arc::new(::grpc::rt::MethodDescriptor {
                name: "/vinyl.Vinyl/Query".to_string(),
                streaming: ::grpc::rt::GrpcStreaming::Unary,
                req_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
                resp_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
            }),
        }
    }
}

impl Vinyl for VinylClient {
    fn login(&self, o: ::grpc::RequestOptions, p: super::transport::LoginRequest) -> ::grpc::SingleResponse<super::transport::LoginResponse> {
        self.grpc_client.call_unary(o, p, self.method_Login.clone())
    }

    fn query(&self, o: ::grpc::RequestOptions, p: super::transport::Request) -> ::grpc::SingleResponse<super::transport::Response> {
        self.grpc_client.call_unary(o, p, self.method_Query.clone())
    }
}

// server

pub struct VinylServer;


impl VinylServer {
    pub fn new_service_def<H : Vinyl + 'static + Sync + Send + 'static>(handler: H) -> ::grpc::rt::ServerServiceDefinition {
        let handler_arc = ::std::sync::Arc::new(handler);
        ::grpc::rt::ServerServiceDefinition::new("/vinyl.Vinyl",
            vec![
                ::grpc::rt::ServerMethod::new(
                    ::std::sync::Arc::new(::grpc::rt::MethodDescriptor {
                        name: "/vinyl.Vinyl/Login".to_string(),
                        streaming: ::grpc::rt::GrpcStreaming::Unary,
                        req_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
                        resp_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
                    }),
                    {
                        let handler_copy = handler_arc.clone();
                        ::grpc::rt::MethodHandlerUnary::new(move |o, p| handler_copy.login(o, p))
                    },
                ),
                ::grpc::rt::ServerMethod::new(
                    ::std::sync::Arc::new(::grpc::rt::MethodDescriptor {
                        name: "/vinyl.Vinyl/Query".to_string(),
                        streaming: ::grpc::rt::GrpcStreaming::Unary,
                        req_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
                        resp_marshaller: Box::new(::grpc::protobuf::MarshallerProtobuf),
                    }),
                    {
                        let handler_copy = handler_arc.clone();
                        ::grpc::rt::MethodHandlerUnary::new(move |o, p| handler_copy.query(o, p))
                    },
                ),
            ],
        )
    }
}
