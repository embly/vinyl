package main.test

import com.apple.foundationdb.record.metadata.MetaDataException
import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition
import com.google.protobuf.{ByteString}
import com.apple.foundationdb.record.{RecordMetaData, RecordMetaDataBuilder}
import com.google.protobuf.DescriptorProtos.{FileDescriptorProto}
import com.google.protobuf.Descriptors.FileDescriptor
import org.scalatest.FunSuite
import com.apple.foundationdb.record.provider.foundationdb.{
  FDBDatabaseFactory,
  FDBMetaDataStore,
  FDBRecordStore
}
import com.apple.foundationdb.record.metadata.{Key}
import run.embly.vinyl.Session
import com.apple.foundationdb.record.provider.foundationdb.RecordStoreNoInfoAndNotEmptyException
import scala.util.{Try, Success, Failure}
import com.apple.foundationdb.Range
import com.apple.foundationdb.record.logging.KeyValueLogMessage

class MainTest extends FunSuite {

  test("session") {
    assertThrows[MetaDataException] {
      Session("hi", ByteString.copyFromUtf8("")).get
    }
  }

  test("with lib") {

    val schemaBuilder = DynamicSchema.newBuilder
    schemaBuilder.setName("foo.proto")
    val msgDef = MessageDefinition
      .newBuilder("Value")
      .addField("optional", "int32", "id", 1)
      .build()

    schemaBuilder.addMessageDefinition(msgDef)
    val schema = schemaBuilder.build
    println("schema.fileDescMap", schema.fileDescMap)
    val msgBuilder = schema.newMessageBuilder("Value")
    val msgDesc = msgBuilder.getDescriptorForType()
    msgBuilder.getDescriptorForType()
    msgBuilder
      .setField(msgDesc.findFieldByName("id"), 1)
      .build()

  }



}
