package run.wasabi.fdb.src.main

import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpace
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpaceDirectory
import com.apple.foundationdb.record.RecordMetaData
import com.apple.foundationdb.record.metadata.Key
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DynamicMessage


//usage example: `i: Int ⇒ 42`


import cats.instances.function

object Main extends App {

  val db = FDBDatabaseFactory
    .instance()
    .getDatabase("/Users/maxm/go/src/github.com/wasabi/fdb/app/fdb.cluster")
  //

  println(s"Hello, World! $db")

  //  val schemaBuilder = DynamicSchema.newBuilder()
  //  schemaBuilder.setName("hi.proto")
  //  val msgDef = MessageDefinition.newBuilder("Person")
  //    .addField("required", "int32", "id", 1)
  //    .addField("required", "string", "name", 2)
  //    .addField("optional", "string", "email", 3)
  //    .build()
  //
  val mFileDescProtoBuilder = FileDescriptorProto.newBuilder()

  val md = new MessageDefinition("Value")
  md.addField(FieldDescriptorProto.Label.LABEL_OPTIONAL, "int32", 1, "id")

  val ud = new MessageDefinition("RecordTypeUnion")
  ud.addField(FieldDescriptorProto.Label.LABEL_OPTIONAL, "Value", 1, "Value")

  val mdType = md.getType
  mFileDescProtoBuilder.addMessageType(mdType)
  mFileDescProtoBuilder.addMessageType(ud.getType)
  val fileDescSetBuilder = FileDescriptorSet.newBuilder()

  val fd = FileDescriptor.buildFrom(
    mFileDescProtoBuilder.build(),
    new Array[FileDescriptor](0)
  )
  //
  //  //  schemaBuilder.addMessageDefinition(msgDef)
  //  //  val file = mFileDescProtoBuilder.build()
  //  //  val schema = schemaBuilder.build()
  //  //  val msgBuilder = schema.newMessageBuilder("Person")
  //  //  val descriptor = msgBuilder.getDescriptorForType()
  //
  //
  val keySpace = new KeySpace(
    new KeySpaceDirectory("hi", KeySpaceDirectory.KeyType.STRING, "hi")
  )
  val path = keySpace.path("hi")
  val builder = RecordMetaData.newBuilder()
  builder.setRecords(fd)
  builder.getRecordType("Value").setPrimaryKey(Key.Expressions.field("id"))


  val msgBuilder = DynamicMessage.newBuilder(mdType)
  println(msgBuilder.getAllFields)
  val msgDesc = msgBuilder.getDescriptorForType
  println(msgDesc.findFieldByName("id"))
  val context = db.openContext()
  val store = FDBRecordStore
    .newBuilder()
    .setMetaDataProvider(builder)
    .setContext(context)
    .setKeySpacePath(path)
    .createOrOpen().saveRecord(
      msgBuilder.setField(msgDesc.findFieldByNumber(1), 1).build())

  //
  //  //  mdb.getRecordType("Value")
  //    .setPrimaryKey(Key.Expressions.field("key"))

}

class MessageDefinition(var name: String) {
  var mMsgTypeBuilder: DescriptorProto.Builder = DescriptorProto.newBuilder()
  mMsgTypeBuilder.setName(name)
  val types = Map(
    "double" -> FieldDescriptorProto.Type.TYPE_DOUBLE,
    "float" -> FieldDescriptorProto.Type.TYPE_FLOAT,
    "int32" -> FieldDescriptorProto.Type.TYPE_INT32,
    "int64" -> FieldDescriptorProto.Type.TYPE_INT64,
    "uint32" -> FieldDescriptorProto.Type.TYPE_UINT32,
    "uint64" -> FieldDescriptorProto.Type.TYPE_UINT64,
    "sint32" -> FieldDescriptorProto.Type.TYPE_SINT32,
    "sint64" -> FieldDescriptorProto.Type.TYPE_SINT64,
    "fixed32" -> FieldDescriptorProto.Type.TYPE_FIXED32,
    "fixed64" -> FieldDescriptorProto.Type.TYPE_FIXED64,
    "sfixed32" -> FieldDescriptorProto.Type.TYPE_SFIXED32,
    "sfixed64" -> FieldDescriptorProto.Type.TYPE_SFIXED64,
    "bool" -> FieldDescriptorProto.Type.TYPE_BOOL,
    "string" -> FieldDescriptorProto.Type.TYPE_STRING,
    "bytes" -> FieldDescriptorProto.Type.TYPE_BYTES
  )

  def addField(
      label: FieldDescriptorProto.Label,
      fieldType: String,
      num: Int,
      name: String
  ): MessageDefinition = {
    val fieldBuilder = FieldDescriptorProto.newBuilder
    fieldBuilder.setLabel(label)
    types.get(fieldType) match {
      case Some(ty) =>
        fieldBuilder.setType(ty)
      case None =>
        fieldBuilder.setTypeName(fieldType)
    }

    fieldBuilder.setName(name).setNumber(num)
    mMsgTypeBuilder.addField(fieldBuilder.build())
    this
  }

  def getType(): DescriptorProto = {
    mMsgTypeBuilder.build()
  }

}
