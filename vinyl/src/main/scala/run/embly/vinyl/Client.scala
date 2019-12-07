package run.embly.vinyl

import com.apple.foundationdb.record.RecordMetaDataProto
import com.apple.foundationdb.record.metadata.{Index, Key}
import com.apple.foundationdb.record.provider.foundationdb.{FDBDatabase, FDBDatabaseFactory}
import com.apple.foundationdb.record.provider.foundationdb.storestate.MetaDataVersionStampStoreStateCacheFactory
import com.google.protobuf.ByteString

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}
import scala.collection.JavaConverters._


class Client {

  implicit val ec = ExecutionContext.global
  val activeSessions: HashMap[String, Session] = HashMap()
  val db = setUpDB()

  def setUpDB(): FDBDatabase = {
    val factory = FDBDatabaseFactory.instance()
    factory.setTrace("./", "vinyl")
    val db = factory.getDatabase()
    db.setStoreStateCache(
      MetaDataVersionStampStoreStateCacheFactory.newInstance().getCache(db)
    )
    db
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
    val token = randomString(32)
    activeSessions += (token -> session)
    Success(token)
  }

  def login(keyspace: String, descriptorBytes: ByteString, records: Seq[vinyl.transport.Record]): Try[String] = {
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
