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
import com.github.os72.protobuf.dynamic.DynamicSchema
import com.google.protobuf.{ByteString, DynamicMessage}
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.FileDescriptor
import org.scalatest.FunSuite
import run.embly.vinyl.Session
import run.embly.vinyl.Client
import scala.collection.JavaConverters._

import scala.util.{Failure, Success, Try}

class DBTest extends FunSuite {

  test("login") {
    val client = new Client()
    val ks = "login"

    assert(
      client
        .login(
          keyspace = ks,
          descriptorBytes = Fixtures.testDescriptorBytes,
          records = Nil
        )
        .failed
        .get
        .getMessage == "Record type User must have a primary key"
    )

    Fixtures.deleteKeyspace(ks, client.db.openContext())

    assert(
      client
        .getSession(
          client
            .login(
              ks,
              Fixtures.testDescriptorBytes,
              Fixtures.records
            )
            .get
        )
        .get
        .metaData
        .getVersion == 0
    )

    assert(
      client
        .getSession(
          client
            .login(
              ks,
              Fixtures.testDescriptorBytes,
              Fixtures.recordsWithEmailIndex
            )
            .get
        )
        .get
        .metaData
        .getVersion == 1
    )

    assert(
      client
        .getSession(
          client
            .login(
              ks,
              Fixtures.testDescriptorBytes,
              Fixtures.recordsWithEmailIndex
            )
            .get
        )
        .get
        .metaData
        .getVersion == 1
    )

    // todo: support index removal
//    assert(client.getSession(client.login(
//      ks, Fixtures.testDescriptorBytes, Fixtures.records
//    ).get).get.metaData.getVersion == 2)

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

    val recordBytes: Array[Byte] = Array(10, 8, 119, 104, 97, 116, 101, 118,
      101, 114, 18, 11, 109, 97, 120, 64, 109, 97, 120, 46, 99, 111, 109)

    val recordDescriptor = session.metaData.getRecordType("User").getDescriptor
    val builder = DynamicMessage.newBuilder(recordDescriptor)
    store.saveRecord(builder.mergeFrom(recordBytes).build())

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
    session.metaData.setVersion(2)

    val recordType = metadata.getRecordType("User")
    recordType.setPrimaryKey(
      Key.Expressions
        .concat(Key.Expressions.recordType(), Key.Expressions.field("id"))
    )

    context = db.openContext()
    var mdStore = session.metaDataStore(context)
    mdStore.saveRecordMetaData(metadata.build())
    mdStore.setLocalFileDescriptor(fileDescriptor)
    context.commit()
    context.close()
    session.metaData = metadata

    context = db.openContext()
    Try(
      FDBRecordStore
        .newBuilder()
        .setMetaDataProvider(session.metaData)
        .setMetaDataStore(mdStore)
        .setContext(context)
        .setKeySpacePath(path.add("data"))
        .createOrOpen()
    ) match {
      case Success(store) =>
        val recordBytes: Array[Byte] = Array(10, 8, 119, 104, 97, 116, 101, 118,
          101, 114, 18, 11, 109, 97, 120, 64, 109, 97, 120, 46, 99, 111, 109)
        val metaData = mdStore.getRecordMetaData()
        val descriptor = metaData.getRecordType("User").getDescriptor
        val builder = DynamicMessage.newBuilder(descriptor)
        val record = builder.mergeFrom(ByteString.copyFrom(recordBytes)).build()
        metaData.getRecordTypeForDescriptor(descriptor);
        store.saveRecord(record)

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
