package keesync

trait Server {
  import scala.concurrent.ExecutionContext.Implicits._
  import scala.concurrent.Future
  import scala.util.{Try , Success, Failure}

  def port: Int

  def inputPrompt: String

  private def timestamp(): String = (new java.util.Date).toString

  def outputPrompt: String = s"[$timestamp]"

  val serverSock = new java.net.ServerSocket(port)

  def log(msg: String) = println(s"\n$outputPrompt $msg")
  def startMsg(): Unit = log(s"Server started, port: $port")
  startMsg()

  protected def spawnAcceptLoop() =  Future {  // par.spawnLoop
    while (true) {
      log("[INFO] Ready to accept new client ...")
      val socket = serverSock.accept()
      log(s"[INFO] New client connected at socket: $socket")
      val channel = Network.Channel.fromSocket(socket)
      log(s"[INFO] New channel created: $channel")
      spawnReadLoop(channel)
    }
  } onFailure { case e: Throwable => log(s"[ERROR] Accept loop terminated: ${e.getMessage()}") }

  private def spawnReadLoop(channel: Network.Channel) =  {
    Future {
      while (true) {
        val msg = channel.read
        log(s"[Info] Got message '$msg'")
        channel.write(s"Echo: '$msg'")
      }
    } onFailure { case e: Throwable => log(s"[ERROR] Read loop terminated: ${e.getMessage()}") }
  }
}
