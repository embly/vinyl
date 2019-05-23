import run.wasabi.fdb.src.main.MessageDefinition
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DynamicMessage
import org.scalatest.FunSuite
import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.{MessageDefinition => MD}

class MainTest extends FunSuite {
  test("my class build with name") {
    val md = new MessageDefinition("Value")
    assert(md.name === "Value")
    assert(md.mMsgTypeBuilder.getName == "Value")

    md.addField(FieldDescriptorProto.Label.LABEL_OPTIONAL, "int32", 1, "id")

    assert(md.mMsgTypeBuilder.getField(0).getName == "id")

    val t = md.getType

    println(t.toString)

    val msgBuilder = DynamicMessage.newBuilder(t)
    println("bad", msgBuilder.getAllFields)
//    val msgDesc = msgBuilder.getDescriptorForType
//    println(msgDesc.findFieldByName("id"))
//    msgBuilder.setField(msgDesc.findFieldByNumber(1), 1).build()
  }
  test("with lib") {
    val schemaBuilder = DynamicSchema.newBuilder
    schemaBuilder.setName("foo.proto")
    val msgDef = MD.newBuilder("Value"
      ).addField("optional", "int32", "id", 1).build()

    schemaBuilder.addMessageDefinition(msgDef)
    val schema = schemaBuilder.build
    val msgBuilder = schema.newMessageBuilder("Value")
    val msgDesc = msgBuilder.getDescriptorForType()
    msgBuilder.getDescriptorForType()
    msgBuilder
      .setField(msgDesc.findFieldByName("id"), 1).build()

  }
}
