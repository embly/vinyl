package run.embly.vinyl

import com.apple.foundationdb.record.RecordMetaDataProto
import com.apple.foundationdb.record.metadata.{Index, Key}
import com.apple.foundationdb.record.provider.foundationdb.{
  FDBDatabase,
  FDBDatabaseFactory,
  FDBRecordContext,
  FDBStoredRecord
}
import com.apple.foundationdb.record.provider.foundationdb.storestate.MetaDataVersionStampStoreStateCacheFactory
import com.google.protobuf.ByteString
import vinyl.transport.{Insert, Request, Response}

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class Client {

  implicit val ec = ExecutionContext.global
  val activeSessions: HashMap[String, Session] = HashMap()
  val db = setUpDB()


  private def setUpDB(): FDBDatabase = {
    val factory = FDBDatabaseFactory.instance()
    // factory.setTrace("./", "vinyl")
    val db = factory.getDatabase()
    db.setStoreStateCache(
      MetaDataVersionStampStoreStateCacheFactory.newInstance().getCache(db)
    )
    db
  }

  def getSession(token: String): Try[Session] = {
    if (!activeSessions.contains(token)) {
      Failure(new Exception("no active session with that token"))
    } else {
      Success(activeSessions(token))
    }
  }


  def query(
      token: String,
      query: Option[vinyl.transport.Query],
      insertions: Seq[vinyl.transport.Insert]
  ): Response = {
    val trySession = getSession(token)
    if (trySession.isFailure) {
      return Response(error = trySession.failed.get.getMessage)
    }
    val session = trySession.get

    def run(context: FDBRecordContext): Response = {
      var resp = Response()
      val tryQuery = for {
        mdStore <- Try(session.metaDataStore(context))
        store <- Try(session.recordStore(context, mdStore))
        _ <- session.processInsertions(store, mdStore, insertions)
        qb <- Try(new QueryBuilder())
        resp <- Try(qb.processQuery(store, session, query))
      } yield resp
      if (tryQuery.isFailure) {
        val err = tryQuery.failed.get
        err.printStackTrace()
        Response(error = err.getMessage)
      } else {
        tryQuery.get
      }
    }
    RunIt.run(db, run)
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

  def genToken(session: Session): Try[String] = {
    val token = randomString(16)
    activeSessions += (token -> session)
    Success(token)
  }

  def login(
      keyspace: String,
      descriptorBytes: ByteString,
      records: Seq[vinyl.transport.Record]
  ): Try[String] = {
    // TODO: auth values and session

    val tryLogin = for {
      session <- Session(keyspace, descriptorBytes)
      _ <- session.addMeteDataRecords(records)
      _ <- session.validateAndStoreMetaData(db.openContext())
      token <- genToken(session)
    } yield token
    tryLogin
  }

}
