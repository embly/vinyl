package run.embly.vinyl

import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory
import com.apple.foundationdb.record.provider.foundationdb.keyspace.{KeySpace, KeySpaceDirectory, KeySpacePath}
import com.apple.foundationdb.record.provider.foundationdb.storestate.MetaDataVersionStampStoreStateCacheFactory
import com.apple.foundationdb
import com.apple.foundationdb.record.provider.foundationdb.FDBStoreTimer
import com.apple.foundationdb.record.{RecordMetaData, RecordMetaDataBuilder, RecordMetaDataProto}
import com.apple.foundationdb.record.query.RecordQuery
import com.apple.foundationdb.record.query.expressions.{Query, QueryComponent}
import com.apple.foundationdb.record.metadata.{Index, Key}
import com.apple.foundationdb.util.LoggableException
import com.apple.foundationdb.record.provider.foundationdb.{FDBMetaDataStore, FDBRecordStore}
import com.apple.foundationdb.record.RecordMetaDataOptionsProto
import com.google.protobuf.ByteString
import com.apple.foundationdb.record.provider.foundationdb.FDBStoreTimer
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.{Descriptor, FileDescriptor}
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Descriptors
import io.grpc.{Server, ServerBuilder}
import io.grpc.stub.StreamObserver

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import vinyl.transport
import vinyl.transport.{ExecuteProperties, LoginRequest, LoginResponse, Request, Response, VinylGrpc}
import java.util.logging.Logger

import run.embly.vinyl.Client

import scala.util.{Failure, Success, Try}

object VinylServer {

  private val logger = Logger.getLogger(classOf[VinylServer].getName)

  def main(args: Array[String]): Unit = {
    // FDBDatabaseFactory.instance().getDatabase().performNoOp()
    // println(s"got that $db")
    val server = new VinylServer(ExecutionContext.global)
    server.start()
    server.blockUntilShutdown()
  }

  private val port = 8090
}

class VinylServer(executionContext: ExecutionContext) { self =>
  private[this] var server: Server = null

  private def start(): Unit = {
    server = ServerBuilder
      .forPort(VinylServer.port)
      .addService(VinylGrpc.bindService(new VinylImpl, executionContext))
      .build
      .start
    VinylServer.logger.info("Server started, listening on " + VinylServer.port)
    sys.addShutdownHook {

      System.err.println(
        "*** shutting down gRPC server since JVM is shutting down"
      )
      self.stop()
      // System.err.println("*** server shut down")
    }
  }

  def wrapQueries(
      query: Seq[transport.QueryComponent]
  ): java.util.List[QueryComponent] = {
    query.map(qc => wrapQuery(qc)).asJava
  }

  def wrapValue(value: transport.Value): Any = {
    return value.valueType match {
      case transport.Value.ValueTypeEnum.DOUBLE => value.double
      case transport.Value.ValueTypeEnum.FLOAT  => value.float
      case transport.Value.ValueTypeEnum.INT32  => value.int32
      case transport.Value.ValueTypeEnum.INT64  => value.int64
      case transport.Value.ValueTypeEnum.SINT32 => value.sint32
      case transport.Value.ValueTypeEnum.SINT64 => value.sint64
      case transport.Value.ValueTypeEnum.BOOL   => value.bool
      case transport.Value.ValueTypeEnum.STRING => value.string
      case transport.Value.ValueTypeEnum.BYTES  => value.bytes
      case _                                    => throw new Exception("no match")

    }
  }

  def wrapField(field: transport.Field): QueryComponent = {
    var queryField = Query.field(field.name);
    return field.componentType match {
      case transport.Field.ComponentType.EQUALS =>
        queryField.equalsValue(wrapValue(field.value.get))
      case transport.Field.ComponentType.GREATER_THAN =>
        queryField.greaterThan(wrapValue(field.value.get))
      case transport.Field.ComponentType.LESS_THAN =>
        queryField.lessThan(wrapValue(field.value.get))
      case transport.Field.ComponentType.EMPTY     => queryField.isEmpty()
      case transport.Field.ComponentType.NOT_EMPTY => queryField.notEmpty()
      case transport.Field.ComponentType.IS_NULL   => queryField.isNull()
      case transport.Field.ComponentType.MATCHES =>
        queryField.matches(wrapQuery(field.matches.get))
      case _ => throw new Exception("no match")

    }
  }

  def wrapQuery(query: transport.QueryComponent): QueryComponent = {
    val children: Seq[transport.QueryComponent] = query.children
    return query.componentType match {
      case transport.QueryComponent.ComponentType.AND =>
        Query.and(wrapQueries(children))
      case transport.QueryComponent.ComponentType.OR =>
        Query.or(wrapQueries(children))
      case transport.QueryComponent.ComponentType.NOT =>
        Query.not(wrapQuery(query.child.get)) // TODO: check for null
      case transport.QueryComponent.ComponentType.FIELD =>
        wrapField(query.field.get)
      case _ => throw new Exception("no match")

    }
  }
  def buildQuery(
      query: transport.Query,
      recordQuery: transport.RecordQuery
  ): RecordQuery = {
    var builder = RecordQuery
      .newBuilder()
    if (recordQuery.filter.isDefined) {
      builder.setFilter(wrapQuery(recordQuery.filter.get))
    }
    builder.setRecordType(query.recordType)
    builder.build()
  }
  def convertExecuteProperties(
      maybeExecuteProperties: Option[transport.ExecuteProperties]
  ): Option[foundationdb.record.ExecuteProperties] = {
    maybeExecuteProperties match {
      case Some(executeProperties) =>
        if (executeProperties.skip == 0 && executeProperties.limit == 0) {
          None
        } else {
          var epBuilder = foundationdb.record.ExecuteProperties.newBuilder()
          if (executeProperties.skip != 0) {
            epBuilder.setSkip(executeProperties.skip)
          }
          if (executeProperties.limit != 0) {
            epBuilder.setReturnedRowLimit(executeProperties.limit)
          }
          // println(s"LIMIT IS ${executeProperties.limit}")
          Some(epBuilder.build())
        }
      case None => None
    }
  }
  def recordQuery(store: FDBRecordStore, query: transport.Query): Response = {
    var response = Response()
    val recordQueryProto: transport.RecordQuery = query.recordQuery.get
    val recordQuery = buildQuery(query, recordQueryProto)
    val cursor = convertExecuteProperties(query.executeProperties) match {
      case Some(executeProperties) =>
        store.executeQuery(recordQuery, null, executeProperties)
      case None => store.executeQuery(recordQuery)
    }

    var msg = cursor.onNext().get

    while (msg.hasNext) {
      // println(s"PRIMARY KEY TIME ${msg.get.getStoredRecord.getPrimaryKey}")
      response = response.addRecords(
        ByteString.copyFrom(msg.get.getStoredRecord.getRecord.toByteArray)
      )
      // println(
      //   s"got cursor message $msg ${msg.get.getStoredRecord.getRecord} $response"
      // )
      msg = cursor.onNext().get
    }
    response
  }
  def loadRecord(
      store: FDBRecordStore,
      session: Session,
      query: transport.Query
  ): Response = {
    val recordType = session.metaData.build().getRecordType(query.recordType)
    var response = Response()
    val tuple = recordType.getRecordTypeKeyTuple.addObject(
      wrapValue(query.primaryKey.get).asInstanceOf[AnyRef]
    )

    val msg = store.loadRecord(tuple)
    // println(s"load record request $msg ${tuple}")
    if (msg != null) {
      response =
        response.addRecords(ByteString.copyFrom(msg.getRecord.toByteArray))
    }
    response
  }
  def deleteRecord(
      store: FDBRecordStore,
      session: Session,
      query: transport.Query
  ): Response = {
    val recordType = session.metaData.build().getRecordType(query.recordType)
    var response = Response()
    val tuple = recordType.getRecordTypeKeyTuple.addObject(
      wrapValue(query.primaryKey.get).asInstanceOf[AnyRef]
    )

    if (!store.deleteRecord(tuple)) {
      Response(error = "record does not exist")
    } else {
      Response()
    }
  }
  def deleteWhere(
      store: FDBRecordStore,
      session: Session,
      query: transport.Query
  ): Response = {
    val recordQueryProto: transport.RecordQuery = query.recordQuery.get
    store.deleteRecordsWhere(
      query.recordType,
      if (recordQueryProto.filter.isDefined)
        wrapQuery(recordQueryProto.filter.get)
      else null
    )
    // TODO: error handling?
    Response()
  }
  def processQuery(
      store: FDBRecordStore,
      session: Session,
      query: transport.Query
  ): Response = {
    query.queryType match {
      case transport.Query.QueryType.RECORD_QUERY => recordQuery(store, query)
      case transport.Query.QueryType.LOAD_RECORD =>
        loadRecord(store, session, query)
      case transport.Query.QueryType.DELETE_RECORD =>
        deleteRecord(store, session, query)
      case transport.Query.QueryType.DELETE_WHERE =>
        deleteWhere(store, session, query)
    }
  }

  val client = new Client()

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class VinylImpl extends VinylGrpc.Vinyl {
    override def login(req: LoginRequest): Future[LoginResponse] = {
      Future.successful(client.login(req.keyspace, req.fileDescriptor, req.records) match {
        case Success(token) => LoginResponse(token=token)
        case Failure(e) => LoginResponse(error=e.getMessage)
      })
    }
    override def query(
        req: Request
    ) = {
      val t0 = System.nanoTime()

      if (!client.activeSessions.contains(req.token)) {
        Future.successful(Response(error = "auth token is invalid"))
      } else {
        val session = client.activeSessions(req.token)

        val context = client.db.openContext()
        val mdStore = new FDBMetaDataStore(context, session.path)
        mdStore.setMaintainHistory(false) // ??
        mdStore.setDependencies(
          Array[Descriptors.FileDescriptor](
            RecordMetaDataOptionsProto.getDescriptor()
          )
        )

        //        println(mdStore.getRecordMetaData().getRecordsDescriptor)
        val keyspacePath = session.path

        for (insertion <- (req.insertions: Seq[vinyl.transport.Insert])) {
          val data: ByteString = insertion.data

          // println(
          //   s"found insertion ${insertion.record} ${data.toByteArray.mkString(" ")}"
          // )

          val descriptor = session.messageDescriptorMap(insertion.record);
          mdStore.setLocalFileDescriptor(descriptor.getFile)
          val builder = DynamicMessage.newBuilder(descriptor);
          builder.mergeFrom(insertion.data: ByteString).build()
          // println(descriptor.getFields)
          println("version " + session.metaData.getVersion)
          val resp = FDBRecordStore
            .newBuilder()
//            .setMetaDataStore(mdStore)
            .setMetaDataProvider(session.metaData.getRecordMetaData())
            .setContext(context)
            .setKeySpacePath(keyspacePath)
            .createOrOpen()
            .saveRecord(
              builder.mergeFrom(insertion.data: ByteString).build()
            )
          //
          // println(s"insertion request response${resp}")
          // println("Insertion complete: " + (System.nanoTime() - t0)/1000 + "ms")
        }
        // println("2: " + (System.nanoTime() - t0)/1000 + "ms")
        var response = Response()
        // println("3: " + (System.nanoTime() - t0)/1000 + "ms")

        val query: Option[vinyl.transport.Query] = req.query
        // println("4: " + (System.nanoTime() - t0)/1000 + "ms")
        if (query.isDefined) {
          // println("processing query")
          // println("Begin processed query: " + (System.nanoTime() - t0)/1000 + "ms")
          val query: vinyl.transport.Query = req.getQuery

          val timer = new FDBStoreTimer()
          context.setTimer(timer)
          // this takes 7ms
          val t1 = System.nanoTime()
          val store = FDBRecordStore
            .newBuilder()
            .setMetaDataProvider(session.metaData)
            .setContext(context)
            .setKeySpacePath(keyspacePath)
            .createOrOpen()
          store.setStateCacheability(true)
          val cache_hit = context
            .getTimer()
            .getCount(FDBStoreTimer.Counts.STORE_STATE_CACHE_HIT)
          println(
            "Processed query: " + (System
              .nanoTime() - t0) / 1000 + "ms cache_hit " + cache_hit
          )

          response = processQuery(store, session, query)
          // println("Processed query: " + (System.nanoTime() - t0)/1000 + "ms")
        }

        try {
          context.commit()
        } catch {
          case uniqueness: foundationdb.record.RecordIndexUniquenessViolation => {
            response = Response(error = uniqueness.getMessage)
          }
          // case default => println("Error commiting to db" + default)
        }
        // println("Commit: " + (System.nanoTime() - t0)/1000 + "ms")
        context.close()
        // println(s"got query request $req $response")
        // println("Context close: " + (System.nanoTime() - t0)/1000 + "ms")

        Future.successful(response)

      }

    }
  }
}
