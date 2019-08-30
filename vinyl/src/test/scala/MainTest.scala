import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition
import com.google.protobuf.{ByteString, Message}
import com.apple.foundationdb.record.query.RecordQuery
import com.apple.foundationdb.record.query.expressions.{Query, QueryComponent}
import com.apple.foundationdb.record.{RecordMetaData, RecordMetaDataBuilder}
import com.google.protobuf.DescriptorProtos.{DescriptorProto, FileDescriptorProto}
import com.google.protobuf.Descriptors.FileDescriptor
import org.scalatest.FunSuite
import vinyl.transport
import collection.mutable.ArrayBuffer
import collection.JavaConverters._


class MainTest extends FunSuite {
  test("with lib") {

    val schemaBuilder = DynamicSchema.newBuilder
    schemaBuilder.setName("foo.proto")
    val msgDef = MessageDefinition.newBuilder("Value"
      ).addField("optional", "int32", "id", 1).build()

    schemaBuilder.addMessageDefinition(msgDef)
    val schema = schemaBuilder.build
    println("schema.fileDescMap", schema.fileDescMap)
    val msgBuilder = schema.newMessageBuilder("Value")
    val msgDesc = msgBuilder.getDescriptorForType()
    msgBuilder.getDescriptorForType()
    msgBuilder
      .setField(msgDesc.findFieldByName("id"), 1).build()

  }

  test("dynamic message") {
    // this test asserts nothing...

    val byteArray: Array[Byte] = Array(10, 12, 116, 97, 98, 108, 101, 115, 46, 112, 114, 111, 116, 111, 34, 44, 10, 4, 85, 115, 101, 114, 18, 14, 10, 2, 105, 100, 24, 1, 32, 1, 40, 9, 82, 2, 105, 100, 18, 20, 10, 5, 101, 109, 97, 105, 108, 24, 2, 32, 1, 40, 9, 82, 5, 101, 109, 97, 105, 108, 34, 45, 10, 15, 82, 101, 99, 111, 114, 100, 84, 121, 112, 101, 85, 110, 105, 111, 110, 18, 26, 10, 5, 95, 85, 115, 101, 114, 24, 1, 32, 1, 40, 11, 50, 5, 46, 85, 115, 101, 114, 82, 4, 85, 115, 101, 114, 66, 6, 90, 4, 109, 97, 105, 110, 98, 6, 112, 114, 111, 116, 111, 51)
    val bytes = ByteString.copyFrom(byteArray)
    println(s"hi $bytes")
    val descriptor: FileDescriptorProto = FileDescriptorProto.parseFrom(bytes.toByteArray)
    val fileDescriptor = FileDescriptor.buildFrom(descriptor, Array());
    var metadata: RecordMetaDataBuilder = RecordMetaData
      .newBuilder()
      .setRecords(fileDescriptor)

  }


  test("query building") {

    val recordQueryBuilder = RecordQuery.newBuilder().setFilter(
      Query.and(
        ArrayBuffer(
          Query.field("price").lessThan(50),
          Query.field("flower").matches(Query.field("type").equalsValue("ROSE"))
        ).asJava
      )
    )

    var query = transport.Query(recordType="Order", filter = Some(transport.QueryComponent(
      children = Array(
        transport.QueryComponent(
          componentType = transport.QueryComponent.ComponentType.FIELD,
          field=Some(transport.Field(
            name="price",
            componentType = transport.Field.ComponentType.LESS_THAN,
            value = Some(transport.Value(int32 = 50, valueType = transport.Value.ValueTypeEnum.INT32))
          ))),
        transport.QueryComponent(
          componentType = transport.QueryComponent.ComponentType.FIELD,
          field=Some(transport.Field(
            name="flower",
            componentType = transport.Field.ComponentType.MATCHES,
            matches = Some(transport.QueryComponent(
              componentType = transport.QueryComponent.ComponentType.FIELD,
              field=Some(transport.Field(
                name="type",
                componentType = transport.Field.ComponentType.EQUALS,
                value = Some(transport.Value(string = "ROSE", valueType = transport.Value.ValueTypeEnum.STRING))
              ))
            ))
          ))
        )
      )
    )))

    assert(recordQueryBuilder.build().getFilter.toString == main.Main.buildQuery(query).getFilter.toString)


  }
}
