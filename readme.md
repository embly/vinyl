# Vinyl

A convenience layer on top of the FoundationDB Record Layer. Will allow:

1.  Defining tables and indexes in code
2.  A protobuf wire format for querying and insertion/deletion
3.  User account management
4.  Caching of metadata and persistence of metadata
5.  Cloudkit-like http layer?

The Record Layer already has record_metadata.proto as an extension to add all necessary metadata
to a parsed protobuf record. Should likely pull out that logic and reimplement it in each language
so that we can just use the same format everywhere. For now we're just going to define our own
metadata (as a convenience), but that should probably change in the future.

<!-- validate(Descriptors.Descriptor) must be called before calling eval(FDBRecordStoreBase, EvaluationContext, FDBRecord), or bad things may happen. -->
