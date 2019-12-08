package run.embly.vinyl

import com.apple.foundationdb.record.metadata.{Index, Key}
import com.apple.foundationdb.record.{
  RecordMetaData,
  RecordMetaDataBuilder,
  RecordMetaDataOptionsProto,
  RecordMetaDataProto
}
import com.apple.foundationdb.record.provider.foundationdb.keyspace.{
  KeySpace,
  KeySpaceDirectory,
  KeySpacePath
}
import com.apple.foundationdb.record.provider.foundationdb.{
  FDBMetaDataStore,
  FDBRecordContext,
  FDBRecordStore
}
import com.google.protobuf.{ByteString, Descriptors, DynamicMessage}
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.{FileDescriptor}
import vinyl.transport.Insert

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class Session private (
    var keySpace: KeySpace,
    var path: KeySpacePath,
    var metaData: RecordMetaDataBuilder
) {
  def metaDataStore(context: FDBRecordContext): FDBMetaDataStore = {
    val out = new FDBMetaDataStore(context, path.add("meta_data"))
    out.setMaintainHistory(false) // ??
    out.setDependencies(
      Array[Descriptors.FileDescriptor](
        RecordMetaDataOptionsProto.getDescriptor()
      )
    )
    out
  }

  def recordStore(
      context: FDBRecordContext,
      mdStore: FDBMetaDataStore
  ): FDBRecordStore = {
    val store = FDBRecordStore
      .newBuilder()
      .setMetaDataProvider(metaData)
      .setMetaDataStore(mdStore)
      .setContext(context)
      .setKeySpacePath(path.add("data"))
      .createOrOpen()
    store.setStateCacheability(true)
    store
  }

  def processInsertions(
      store: FDBRecordStore,
      mdStore: FDBMetaDataStore,
      insertions: Seq[Insert]
  ): Try[Unit] = {
    for (insertion <- insertions) {
      val descriptor =
        mdStore.getRecordMetaData.getRecordType(insertion.record).getDescriptor
      val builder = DynamicMessage.newBuilder(descriptor)
      var _resp = store.saveRecord(
        builder.mergeFrom(insertion.data: ByteString).build()
      )
      // todo: return resp
    }
    Success(())
  }

  def validateAndStoreMetaData(context: FDBRecordContext): Try[Unit] = {
    val mdStore = metaDataStore(context)
    val out: Try[Unit] = Try(mdStore.getRecordMetaData()) match {
      case Success(md) => {
        if (md.getVersion > metaData.getVersion) {
          metaData.setVersion(md.getVersion)
        }
        if (md.toProto() != metaData.build().toProto()) {
          metaData.setVersion(md.getVersion() + 1)
          Try(mdStore.saveRecordMetaData(metaData))
        } else {
          Success(())
        }
      }
      case Failure(_) => {
        Try(mdStore.saveRecordMetaData(metaData))
      }
    }
    if (out.isSuccess) {
      context.commit()
    }
    context.close()
    out
  }
  def addMeteDataRecords(records: Seq[vinyl.transport.Record]): Try[Unit] = {
    for (record <- records) {
      val recordType = metaData.getRecordType(record.name)

      val fieldOptions: Map[String, vinyl.transport.FieldOptions] =
        record.fieldOptions

      for ((name, fieldOption) <- fieldOptions) {
        val idx: Option[vinyl.transport.FieldOptions.IndexOption] =
          fieldOption.index

        if (fieldOption.primaryKey) {
          println(s"Adding primary key '$name' to '${record.name}'")
          recordType.setPrimaryKey(
            Key.Expressions.concat(
              Key.Expressions.recordType(),
              Key.Expressions.field(name)
            )
          )

        } else if (idx.isDefined && idx.get.`type` == "value") {
          val index_name = record.name + "." + name
          val unique: Boolean = idx.get.unique
          val options: java.util.List[RecordMetaDataProto.Index.Option] =
            Nil.asJava
          metaData.addIndex(
            record.name: String,
            new Index(
              index_name,
              Key.Expressions.field(name),
              "value",
              Index.buildOptions(options, unique)
            )
          )
          // TODO: unique indexes
        }
      }
    }
    metaData.build(true)
    Success()
  }

}

object Session {
  def safeParse(d: Array[Byte]): Try[FileDescriptor] =
    Try(FileDescriptor.buildFrom(FileDescriptorProto.parseFrom(d), Array()))

  def createSession(
      fileDescriptor: FileDescriptor,
      ks: String
  ): Try[Session] = {
    val metadata: RecordMetaDataBuilder = RecordMetaData
      .newBuilder()
      .setRecords(fileDescriptor)

    var keyspace = new KeySpace(
      new KeySpaceDirectory(
        ks,
        KeySpaceDirectory.KeyType.STRING,
        ks
      ).addSubdirectory(
          new KeySpaceDirectory(
            "meta_data",
            KeySpaceDirectory.KeyType.STRING,
            "m"
          )
        )
        .addSubdirectory(
          new KeySpaceDirectory("data", KeySpaceDirectory.KeyType.STRING, "d")
        )
    )
    var path = keyspace.path(ks)

    Success(new Session(keyspace, path, metadata))
  }

  def apply(ks: String, descriptorBytes: ByteString): Try[Session] = {
    val trySession = for {
      fileDescriptor <- safeParse(descriptorBytes.toByteArray)
      session <- createSession(fileDescriptor, ks)
    } yield session
    trySession
  }

}
