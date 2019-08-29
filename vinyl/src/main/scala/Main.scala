package main

import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpace
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpaceDirectory
import com.apple.foundationdb.record.{RecordMetaData, RecordMetaDataBuilder}
import com.apple.foundationdb.record.metadata.{Key, Index}
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore
import com.apple.foundationdb.tuple.Tuple
import com.apple.foundationdb.record.provider.foundationdb.FDBStoredRecord
import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition
import com.google.protobuf.{Message, ByteString}
import com.google.protobuf.DescriptorProtos.{
  FileDescriptorProto,
  DescriptorProto
}
import com.google.protobuf.Descriptors.{FileDescriptor, Descriptor}
import com.google.protobuf.DynamicMessage
import scala.io.Source
import akka.http.scaladsl.Http2
import akka.http.scaladsl.HttpConnectionContext
import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, Http2}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{HttpCookiePair, HttpCookie}
import scala.concurrent.{Await, Future}
import akka.stream.ActorMaterializer
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.collection.mutable.HashMap
import scala.concurrent.{ExecutionContext}
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBSupport

class Session(ks: String, descriptorBytes: ByteString) {

  private val descriptor: FileDescriptorProto =
    FileDescriptorProto.parseFrom(descriptorBytes.toByteArray)

  private val fileDescriptor = FileDescriptor.buildFrom(descriptor, Array());
  var metadata: RecordMetaDataBuilder = RecordMetaData
    .newBuilder()
    .setRecords(fileDescriptor)

  var keySpace: String = ks

  var messageDescriptorMap: HashMap[String, Descriptor] = HashMap()
  for (messageType <- fileDescriptor.getMessageTypes().asScala) {
    println(s"registering descriptor for type: ${messageType.getName}")
    messageDescriptorMap += (messageType.getName -> messageType)
  }

}

object Main extends App with ScalaPBSupport {

  def randomString(length: Int) = {
    val chars = ('a' to 'z') ++ ('0' to '9')
    val sb = new StringBuilder
    for (i <- 1 to length) {
      val randomNum = util.Random.nextInt(chars.length)
      sb.append(chars(randomNum))
    }
    sb.toString
  }

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit val ec = ExecutionContext.global

  val activeSessions: HashMap[String, Session] = HashMap()

  def isActiveSession(token: Option[HttpCookiePair]): Boolean = {
    if (token.isEmpty) {
      return false
    }
    activeSessions.contains(token.get.value)
  }
  val routes: Route = entity(as[vinyl.messages.Query]) {
    query: vinyl.messages.Query =>
      {
        concat(
          path("start") {
            val descriptorBytes: ByteString = query.fileDescriptor
            val session = new Session(
              query.keyspace,
              descriptorBytes
            )
            println(s"descriptor ${descriptorBytes.toByteArray.mkString(" ")}")
            val tables: Seq[vinyl.messages.Table] = query.tables
            for (table <- tables) {
              val recordType = session.metadata.getRecordType(table.name)

              val fieldOptions: Map[String, vinyl.messages.FieldOptions] =
                table.fieldOptions

              for ((name, fieldOption) <- fieldOptions) {
                val idx: Option[vinyl.messages.FieldOptions.IndexOption] =
                  fieldOption.index

                if (fieldOption.primaryKey) {
                  println(s"Adding primary key '$name' to '${table.name}'")
                  recordType.setPrimaryKey(Key.Expressions.field(name))
                } else if (idx.isDefined && idx.get.`type` == "value") {
                  println(s"Adding index to '${table.name}' for field '$name'")
                  session.metadata.addIndex(
                    table.name: String,
                    new Index("todoIndex", Key.Expressions.field(name))
                  )
                }
              }
            }

            val token = randomString(32)
            println(
              s"Starting new session with token: ${token} ${query.keyspace}"
            )
            // TODO: auth values and session
            activeSessions += (token -> session)
            setCookie(HttpCookie("vinyl-token", value = token)) {
              complete("ok")
            }
          },
          optionalCookie("vinyl-token") { cookie =>
            authorize(isActiveSession(cookie)) {
              val session = activeSessions(cookie.get.value)

              println(s"Got authed query request")

              val context = db.openContext()
              val keySpace = new KeySpace(
                new KeySpaceDirectory(
                  session.keySpace,
                  KeySpaceDirectory.KeyType.STRING,
                  session.keySpace
                )
              )
              val keyspacePath = keySpace.path(session.keySpace)
              for (insertion <- (query.insertions: Seq[vinyl.messages.Insert])) {
                val data: ByteString = insertion.data

                println(
                  s"found insertion ${insertion.table} ${data.toByteArray.mkString(" ")}"
                )

                val descriptor = session.messageDescriptorMap(insertion.table);
                val builder = DynamicMessage.newBuilder(descriptor);
                builder.mergeFrom(insertion.data: ByteString).build()
                  println(descriptor.getFields)
                val resp = FDBRecordStore
                  .newBuilder()
                  .setMetaDataProvider(session.metadata)
                  .setContext(context)
                  .setKeySpacePath(keyspacePath)
                  .createOrOpen()
                  .saveRecord(
                    builder.mergeFrom(insertion.data: ByteString).build()
                  )
//
                println(resp)

              }

              context.commit()
              context.close()

              complete("ok")
            }
          }
        )
      }
  }

  val asyncHandler: HttpRequest => Future[HttpResponse] =
    Route.asyncHandler(routes)

  val http2Future = Http2().bindAndHandleAsync(
    asyncHandler,
    interface = "0.0.0.0",
    port = 8090,
    connectionContext = HttpConnectionContext()
  )

  val http2 = Await.result(http2Future, Duration.Inf)
  println(
    "HTTP/2 server is listening on http://localhost:8090 or https://localhost:8090"
  )

  val db = FDBDatabaseFactory
    .instance()
    .getDatabase()

  println(s"Hello, World! $db")

  val schemaBuilder = DynamicSchema.newBuilder()
  schemaBuilder.setName("hi.proto")
  schemaBuilder.addMessageDefinition(
    MessageDefinition
      .newBuilder("Value")
      .addField("required", "int64", "id", 1)
      .addField("optional", "string", "email", 3)
      .build()
  )

  schemaBuilder.addMessageDefinition(
    MessageDefinition
      .newBuilder("RecordTypeUnion")
      .addField("optional", "Value", "_Value", 1)
      .build()
  )
  val schema = schemaBuilder.build()

  val keySpace = new KeySpace(
    new KeySpaceDirectory("foo", KeySpaceDirectory.KeyType.STRING, "foo")
  )
  val keyspacePath = keySpace.path("foo")
  val builder = RecordMetaData.newBuilder()
  builder.setRecords(schema.fileDescMap.get("hi.proto"))
  builder.getRecordType("Value").setPrimaryKey(Key.Expressions.field("id"))

  val msgBuilder = schema.newMessageBuilder("Value")
  val msgDesc = msgBuilder.getDescriptorForType

  val context = db.openContext()

  val id: java.lang.Long = 4: Long

  val resp = FDBRecordStore
    .newBuilder()
    .setMetaDataProvider(builder)
    .setContext(context)
    .setKeySpacePath(keyspacePath)
    .createOrOpen()
    .saveRecord(
      msgBuilder
        .setField(msgDesc.findFieldByName("id"), id)
        .setField(msgDesc.findFieldByName("email"), "hi")
        .build()
    )
  println(resp)

  context.commit()
  context.close()

  val nextContext = db.openContext()

  val storedRecord: FDBStoredRecord[Message] = FDBRecordStore
    .newBuilder()
    .setMetaDataProvider(builder)
    .setContext(nextContext)
    .setKeySpacePath(keyspacePath)
    .createOrOpen()
    .loadRecord(Tuple.from(id))
  nextContext.close()
  println(storedRecord)

  val msgBldr = schema.newMessageBuilder("Value")
  val value = msgBldr.mergeFrom(storedRecord.getRecord).build()
  println(value)

}
