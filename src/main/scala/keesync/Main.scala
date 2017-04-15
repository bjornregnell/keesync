package keesync

object Main {

  def main(args: Array[String]): Unit = {
    println("Welcome to keesync -- a sync server for keehive")
    val port = scala.util.Try(args(0).toInt).getOrElse(25470)
    val server = new SyncServer(port)
    server.start()
  }

}
