package main.test

import com.apple.foundationdb.Range
import com.apple.foundationdb.record.{RecordMetaData, RecordMetaDataBuilder}
import com.apple.foundationdb.record.logging.KeyValueLogMessage
import com.apple.foundationdb.record.metadata.{Key, MetaDataException}
import com.apple.foundationdb.record.provider.foundationdb.{
  FDBDatabaseFactory,
  FDBMetaDataStore,
  FDBRecordStore,
  RecordStoreNoInfoAndNotEmptyException
}
import com.google.protobuf.ByteString
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.FileDescriptor
import org.scalatest.FunSuite
import run.embly.vinyl.Session
import run.embly.vinyl.Client
import scala.util.{Failure, Success, Try}

class DBTest extends FunSuite {

  test("login") {
    val client = new Client()
    val ks = "logins"

    assert(client.login(
      keyspace = "h",
      descriptorBytes = Fixtures.testDescriptorBytes,
      records = Nil
    ).failed.get.getMessage == "Record type User must have a primary key")

    Fixtures.deleteKeyspace(ks, client.db.openContext())

    assert(client.getSession(client.login(
      ks, Fixtures.testDescriptorBytes, Fixtures.records
    ).get).get.metaData.getVersion == 0)

    assert(client.getSession(client.login(
      ks, Fixtures.testDescriptorBytes, Fixtures.records
    ).get).get.metaData.getVersion == 0)

    assert(client.getSession(client.login(
      ks, Fixtures.testDescriptorBytes, Fixtures.recordsWithEmailIndex
    ).get).get.metaData.getVersion == 1)

  }

  test("dynamic message") {

    assertThrows[MetaDataException] {
      Session("hi", ByteString.copyFromUtf8("")).get
    }
    val db = FDBDatabaseFactory.instance().getDatabase()

    val session = Session("hi", Fixtures.testDescriptorBytes).get
    var context = db.openContext()
    val metaDataSubspace = session.path.toSubspace(context)
    context.ensureActive.clear(Range.startsWith(metaDataSubspace.pack))
    context.commit()
    context.close()

    assert(session.path.toString() == "/hi:\"hi\"")

    val descriptor: FileDescriptorProto =
      FileDescriptorProto.parseFrom(Fixtures.testDescriptorBytes.toByteArray)
    val fileDescriptor = FileDescriptor.buildFrom(descriptor, Array());
    var metadata: RecordMetaDataBuilder = RecordMetaData
      .newBuilder()
      .setRecords(fileDescriptor)
    val recordType = session.metaData.getRecordType("User")
    recordType.setPrimaryKey(
      Key.Expressions
        .concat(Key.Expressions.recordType(), Key.Expressions.field("id"))
    )
    context = db.openContext()
    val store = FDBRecordStore
      .newBuilder()
      .setMetaDataProvider(session.metaData)
      .setContext(context)
      .setKeySpacePath(session.path)
      .createOrOpen()
    db.close()
  }

  test("metadata store") {

    assertThrows[MetaDataException] {
      Session("hi", ByteString.copyFromUtf8("")).get
    }

    val session = Session("hi", Fixtures.testDescriptorBytes).get
    val path = session.path

    val db = FDBDatabaseFactory.instance().getDatabase()
    var context = db.openContext()
    val metaDataSubspace = path.toSubspace(context)
    context.ensureActive.clear(Range.startsWith(metaDataSubspace.pack))
    context.commit()
    context.close()

    assert(path.toString() == "/hi:\"hi\"")
    val descriptor: FileDescriptorProto =
      FileDescriptorProto.parseFrom(Fixtures.testDescriptorBytes.toByteArray)
    val fileDescriptor = FileDescriptor.buildFrom(descriptor, Array());
    var metadata: RecordMetaDataBuilder = RecordMetaData
      .newBuilder()
      .setRecords(fileDescriptor)
    metadata.setVersion(2)

    val recordType = metadata.getRecordType("User")
    recordType.setPrimaryKey(
      Key.Expressions
        .concat(Key.Expressions.recordType(), Key.Expressions.field("id"))
    )

    context = db.openContext()
    val mdStore = session.metaDataStore(context)
    mdStore.saveRecordMetaData(metadata)
    mdStore.setLocalFileDescriptor(fileDescriptor)
    context.commit()
    context.close()

    context = db.openContext()
    Try(
      FDBRecordStore
        .newBuilder()
        .setMetaDataProvider(metadata.build(true))
        .setMetaDataStore(mdStore)
        .setContext(context)
        .setKeySpacePath(path.add("data"))
        .createOrOpen()
    ) match {
      case Success(store) =>
        println("we did it ", store)
      case Failure(e) =>
        println("oh no", e)
        e match {
          case err: RecordStoreNoInfoAndNotEmptyException =>
            val msg = KeyValueLogMessage
              .build("some message")
              .addKeysAndValues(err.getLogInfo())
              .toString()
            println("msg " + msg)
        }
        assert(false)
    }

    context.close()
    db.close()
  }

}
