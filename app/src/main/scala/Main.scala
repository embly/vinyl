package main

import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpace
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpaceDirectory
import com.apple.foundationdb.record.RecordMetaData
import com.apple.foundationdb.record.metadata.Key
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore
import com.apple.foundationdb.tuple.Tuple
import com.apple.foundationdb.record.provider.foundationdb.FDBStoredRecord
import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition
import com.google.protobuf.Message

object Main extends App {

  val db = FDBDatabaseFactory
    .instance()
    .getDatabase("/Users/maxm/go/src/github.com/wasabi/fdb/app/fdb.cluster")

  println(s"Hello, World! $db")

  val schemaBuilder = DynamicSchema.newBuilder()
  schemaBuilder.setName("hi.proto")
  schemaBuilder.addMessageDefinition(MessageDefinition.newBuilder("Value")
    .addField("required", "int64", "id", 1)
    .addField("optional", "string", "email", 3)
    .build())
  schemaBuilder.addMessageDefinition(MessageDefinition.newBuilder("RecordTypeUnion")
    .addField("optional", "Value", "_Value", 1)
    .build())
  val schema = schemaBuilder.build()

  val keySpace = new KeySpace(
    new KeySpaceDirectory("foo", KeySpaceDirectory.KeyType.STRING, "foo")
  )
  val path = keySpace.path("foo")
  val builder = RecordMetaData.newBuilder()
  builder.setRecords(schema.fileDescMap.get("hi.proto"))
  builder.getRecordType("Value").setPrimaryKey(Key.Expressions.field("id"))


  val msgBuilder = schema.newMessageBuilder("Value")
  val msgDesc = msgBuilder.getDescriptorForType()

  val context = db.openContext()


  val id: java.lang.Long = 4: Long

  val resp = FDBRecordStore
    .newBuilder()
    .setMetaDataProvider(builder)
    .setContext(context)
    .setKeySpacePath(path)
    .createOrOpen().saveRecord(
    msgBuilder
      .setField(msgDesc.findFieldByName("id"), id)
      .setField(msgDesc.findFieldByName("email"), "hi")
      .build())
  println(resp)

  context.commit()
  context.close()

  val nextContext = db.openContext()

  val storedRecord: FDBStoredRecord[Message] = FDBRecordStore
    .newBuilder()
    .setMetaDataProvider(builder)
    .setContext(nextContext)
    .setKeySpacePath(path)
    .createOrOpen().loadRecord(Tuple.from(id))
  nextContext.close()
  println(storedRecord)

  val msgBldr = schema.newMessageBuilder("Value")
  val value = msgBldr.mergeFrom(storedRecord.getRecord).build()
  println(value)

}

