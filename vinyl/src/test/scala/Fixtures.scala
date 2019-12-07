package main.test
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext
import com.google.protobuf.ByteString
import run.embly.vinyl.Session
import com.apple.foundationdb.Range

object Fixtures {

  def getSession(ks: String): Session = {
    Session(ks, testDescriptorBytes).get
  }

  def deleteKeyspace(ks: String, context: FDBRecordContext) = {
    val metaDataSubspace = getSession(ks).path.toSubspace(context)
    context.ensureActive.clear(Range.startsWith(metaDataSubspace.pack))
    context.commit()
    context.close()
  }

  private val sampleProtoByteArray: Array[Byte] = Array(10, 12, 116, 97, 98,
    108, 101, 115, 46, 112, 114, 111, 116, 111, 34, 44, 10, 4, 85, 115, 101,
    114, 18, 14, 10, 2, 105, 100, 24, 1, 32, 1, 40, 9, 82, 2, 105, 100, 18, 20,
    10, 5, 101, 109, 97, 105, 108, 24, 2, 32, 1, 40, 9, 82, 5, 101, 109, 97,
    105, 108, 34, 45, 10, 15, 82, 101, 99, 111, 114, 100, 84, 121, 112, 101, 85,
    110, 105, 111, 110, 18, 26, 10, 5, 95, 85, 115, 101, 114, 24, 1, 32, 1, 40,
    11, 50, 5, 46, 85, 115, 101, 114, 82, 4, 85, 115, 101, 114, 66, 6, 90, 4,
    109, 97, 105, 110, 98, 6, 112, 114, 111, 116, 111, 51)
  val testDescriptorBytes = ByteString.copyFrom(sampleProtoByteArray)

  val records: Seq[vinyl.transport.Record] = Seq(
    vinyl.transport.Record(
      fieldOptions = Map[String, vinyl.transport.FieldOptions](
        "id" -> vinyl.transport.FieldOptions(
          primaryKey = true
        )
      ),
      name = "User"
    )
  )

  val recordsWithEmailIndex: Seq[vinyl.transport.Record] = Seq(
    vinyl.transport.Record(
      fieldOptions = Map[String, vinyl.transport.FieldOptions](
        "id" -> vinyl.transport.FieldOptions(
          primaryKey = true
        ),
        "email" -> vinyl.transport.FieldOptions(
          index = Some(vinyl.transport.FieldOptions.IndexOption("value"))
        )
      ),
      name = "User"
    )
  )

}
