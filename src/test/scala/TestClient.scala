package keesync

object TestClient {
  def main(args: Array[String]): Unit = {
    val c = new Client(port = 50001)
    c.hello
    c.start
  }
}
