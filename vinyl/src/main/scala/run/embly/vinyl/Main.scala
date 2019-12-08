package run.embly.vinyl

import io.grpc.{Server, ServerBuilder}
import scala.concurrent.{ExecutionContext, Future}
import vinyl.transport.{
  LoginRequest,
  LoginResponse,
  Request,
  Response,
  VinylGrpc
}
import java.util.logging.Logger
import scala.util.{Failure, Success}

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
      System.err.println("*** server shut down")
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
      println("login request: "+req)
      Future.successful(
        client.login(req.keyspace, req.fileDescriptor, req.records) match {
          case Success(token) => LoginResponse(token = token)
          case Failure(e)     =>
            println(e.printStackTrace())
            LoginResponse(error = e.getMessage)
        }
      )
    }
    override def query(
        req: Request
    ): Future[Response] = {
      println("query request: "+req)
      Future.successful(client.query(req.token, req.query, req.insertions))
    }
  }
}
