package run.embly.vinyl

import com.apple.foundationdb
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore
import com.apple.foundationdb.record.query.RecordQuery
import com.apple.foundationdb.record.query.expressions.{Query, QueryComponent}
import com.google.protobuf.ByteString
import vinyl.transport
import vinyl.transport.Response
import scala.collection.JavaConverters._

class QueryBuilder {
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
      query: Option[transport.Query]
  ): Response = {
    query match {
      case Some(q) =>
        q.queryType match {
          case transport.Query.QueryType.RECORD_QUERY =>
            recordQuery(store, q)
          case transport.Query.QueryType.LOAD_RECORD =>
            loadRecord(store, session, q)
          case transport.Query.QueryType.DELETE_RECORD =>
            deleteRecord(store, session, q)
          case transport.Query.QueryType.DELETE_WHERE =>
            deleteWhere(store, session, q)
        }
      case None =>
        Response()
    }
  }

}
