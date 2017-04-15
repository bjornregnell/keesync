package keesync

object TestClient {
  def main(args: Array[String]): Unit = {
    val host = args.lift(0).getOrElse("localhost")
    val port = scala.util.Try(args(1).toInt).getOrElse(25470)
    val c = new Client(host, port)
    c.hello
    c.start
  }
}
