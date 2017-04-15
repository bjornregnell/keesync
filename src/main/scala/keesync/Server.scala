package keesync

trait Server {
  import scala.concurrent.ExecutionContext.Implicits._
  import scala.concurrent.Future
  import scala.util.{Try , Success, Failure}

  def port: Int

  def inputPrompt: String

  trait Message { def msg : String }
  case class Request (msg: String) extends Message
  case class Response(msg: String) extends Message
  type Dispatcher = Request => Response

  private def timestamp(): String = (new java.util.Date).toString

  def outputPrompt: String = s"[$timestamp]"

  val serverPort = {
    val p = Network.openServerPort(port)
    log(s"[INFO] Ready to accept new client from $p")
    p
  }

  def log(msg: String) = println(s"\n$outputPrompt $msg")
  def startMsg(): Unit = log(s"Server started, port: $port")

  protected def spawnAcceptLoop() =  Future {  // par.spawnLoop
    while (true) {
      val connection = Network.Connection.toClient(from = serverPort)
      log(s"[INFO] New connection created: $connection")
      spawnReadLoop(connection)
    }
  } onFailure { case e: Throwable => log(s"[ERROR] Accept loop terminated: ${e.getMessage()}") }

  private def spawnReadLoop(connection: Network.Connection) =  {
    Future {
      while (true) {
        val msg = connection.read
        log(s"[Info] Got message '$msg'")
        connection.write(s"Echo: '$msg'")
      }
    } onFailure { case e: Throwable => log(s"[ERROR] Read loop terminated: ${e.getMessage()}") }
  }
}
