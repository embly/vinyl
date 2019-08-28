import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition


import org.scalatest.FunSuite


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
}
