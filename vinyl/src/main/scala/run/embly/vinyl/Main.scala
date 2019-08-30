package main

import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpace
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpaceDirectory
import com.apple.foundationdb.record.{RecordMetaData, RecordMetaDataBuilder}
import com.apple.foundationdb.record.query.RecordQuery
import com.apple.foundationdb.record.query.expressions.{Query, QueryComponent}
import com.apple.foundationdb.record.metadata.{Index, Key}
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore
import com.apple.foundationdb.tuple.Tuple
import com.apple.foundationdb.record.provider.foundationdb.FDBStoredRecord
import com.github.os72.protobuf.dynamic.DynamicSchema
import com.github.os72.protobuf.dynamic.MessageDefinition
import com.google.protobuf.{ByteString, Message}
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.{Descriptor, FileDescriptor}
import com.google.protobuf.DynamicMessage
import akka.http.scaladsl.HttpConnectionContext
import akka.actor.ActorSystem
import akka.http.scaladsl.Http2
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair}

import scala.concurrent.{Await, Future}
import akka.stream.ActorMaterializer

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBBinarySupport
import vinyl.transport

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

object Main extends App with ScalaPBBinarySupport {

  def wrapQueries(query: Seq[transport.QueryComponent]): java.util.List[QueryComponent] = {
    query.map(qc => wrapQuery(qc)).asJava
  }
  def wrapValue(value: transport.Value): Any = {
    return value.valueType match {
      case transport.Value.ValueTypeEnum.DOUBLE => value.double
      case transport.Value.ValueTypeEnum.FLOAT => value.float
      case transport.Value.ValueTypeEnum.INT32 => value.int32
      case transport.Value.ValueTypeEnum.INT64 => value.int64
      case transport.Value.ValueTypeEnum.SINT32 => value.sint32
      case transport.Value.ValueTypeEnum.SINT64 => value.sint64
      case transport.Value.ValueTypeEnum.BOOL => value.bool
      case transport.Value.ValueTypeEnum.STRING => value.string
      case transport.Value.ValueTypeEnum.BYTES => value.bytes

    }
  }
  def wrapField(field: transport.Field): QueryComponent = {
    var queryField = Query.field(field.name);
    return field.componentType match {
      case transport.Field.ComponentType.EQUALS => queryField.equalsValue(wrapValue(field.value.get))
      case transport.Field.ComponentType.GREATER_THAN => queryField.greaterThan(wrapValue(field.value.get))
      case transport.Field.ComponentType.LESS_THAN => queryField.lessThan(wrapValue(field.value.get))
      case transport.Field.ComponentType.EMPTY => queryField.isEmpty()
      case transport.Field.ComponentType.NOT_EMPTY => queryField.notEmpty()
      case transport.Field.ComponentType.IS_NULL => queryField.isNull()
      case transport.Field.ComponentType.MATCHES => queryField.matches(wrapQuery(field.matches.get))
    }
  }
  def wrapQuery(query: transport.QueryComponent): QueryComponent = {
    val children: Seq[transport.QueryComponent] = query.children
    return query.componentType match {
      case transport.QueryComponent.ComponentType.AND => Query.and(wrapQueries(children))
      case transport.QueryComponent.ComponentType.OR => Query.or(wrapQueries(children))
      case transport.QueryComponent.ComponentType.NOT => Query.not(wrapQuery(query.child.get)) // TODO: check for null
      case transport.QueryComponent.ComponentType.FIELD => wrapField(query.field.get)
    }
  }


  def buildQuery(query: transport.Query): RecordQuery = {
    RecordQuery.newBuilder().setRecordType(query.recordType).setFilter(wrapQuery(query.filter.get)).build()
  }


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

  val routes: Route = entity(as[vinyl.transport.Request]) {
    request: vinyl.transport.Request =>
      {
        concat(
          path("start") {
            val descriptorBytes: ByteString = request.fileDescriptor
            val session = new Session(
              request.keyspace,
              descriptorBytes
            )
            println(s"descriptor ${descriptorBytes.toByteArray.mkString(" ")}")
            val tables: Seq[vinyl.transport.Table] = request.tables
            for (table <- tables) {
              val recordType = session.metadata.getRecordType(table.name)

              val fieldOptions: Map[String, vinyl.transport.FieldOptions] =
                table.fieldOptions

              for ((name, fieldOption) <- fieldOptions) {
                val idx: Option[vinyl.transport.FieldOptions.IndexOption] =
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
              s"Starting new session with token: ${token} ${request.keyspace}"
            )


            // TODO: auth values and session
            activeSessions += (token -> session)
            setCookie(HttpCookie("vinyl-token", value = token)) {
              complete(vinyl.transport.Response())
            }
          },
          optionalCookie("vinyl-token") { cookie =>
            authorize(isActiveSession(cookie)) {
              val session = activeSessions(cookie.get.value)

              println(s"Got authed request")

              val context = db.openContext()
              val keySpace = new KeySpace(
                new KeySpaceDirectory(
                  session.keySpace,
                  KeySpaceDirectory.KeyType.STRING,
                  session.keySpace
                )
              )

              val keyspacePath = keySpace.path(session.keySpace)
              for (insertion <- (request.insertions: Seq[vinyl.transport.Insert])) {
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
                println(s"insertion request response${resp}")

              }

              var response = vinyl.transport.Response()
              val query: Option[vinyl.transport.Query] = request.query

              if (query.isDefined) {
                println("processing query")
                val query: vinyl.transport.Query = request.getQuery
                val recordQuery = buildQuery(query)
                val store = FDBRecordStore
                  .newBuilder()
                  .setMetaDataProvider(session.metadata)
                  .setContext(context)
                  .setKeySpacePath(keyspacePath)
                  .createOrOpen()
                val cursor = store.executeQuery(recordQuery)
                cursor.forEach(msg => {
                  response = response.addRecords(ByteString.copyFrom(msg.getStoredRecord.getRecord.toByteArray))
                })
              }

              context.commit()
              context.close()

              complete(response)
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
