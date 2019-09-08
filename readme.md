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

### Notes from the paper and elsewhere

The Record Layer is complex and I am actively learning about its structure and characteristics. Notes here are intended to reference various complexity and considerations. Some of these are being implemented, some of them are being ignored for the time being.

[The Record Layer Payer](https://www.foundationdb.org/files/record-layer-paper.pdf)

**Make sure descriptors are validated**

This is mentioned [in the docs](https://static.javadoc.io/org.foundationdb/fdb-record-layer-core-pb3/2.5.40.0/com/apple/foundationdb/record/query/expressions/QueryComponent.html). There is the general idea of validating descriptors. This surely places constraints on what tables can be created and how they can be changed over time. The api should be considerate of this:

> validate(Descriptors.Descriptor) must be called before calling eval(FDBRecordStoreBase, EvaluationContext, FDBRecord), or bad things may happen.

**Index definition and maintenance**

Paper, section 5:

> Instead, the index is disabled and the reindexing proceeds as a background job, as described in Section 6.

Is this entirely handled by the client or are there behaviors here that need to be considered.

**Using primary keys in table relationships**

Primary keys are all in the same index. Paper section 10.2:

> For example, there is a single extent for
> all record types because CloudKit has untyped foreign-key
> references without a “table” association. By default, selecting all records of a particular type requires a full scan that
> skips over records of other types or maintaining secondary
> indexes.

> For clients who do not need this shared extent, we
> now support emulating separate extents for each record type
> by adding a type-specific prefix to the primary key.

Likely make sure this is surfaced as a configuration option or supported by default
