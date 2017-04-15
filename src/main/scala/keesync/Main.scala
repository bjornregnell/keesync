package keesync

object Main {

  val path  = s"${Disk.userDir}/keesync-data"

  def main(args: Array[String]): Unit = {
    println("Welcome to keesync -- a sync server for keehive")
    if (Disk.createDirIfNotExist(path))
      println(s"Created directory: $path")
    val port = scala.util.Try(args(0).toInt).getOrElse(25470)
    val server = new Server(port)
    server.start()
  }

  def abort(errMsg: String): Unit = { println(s"Error: $errMsg"); sys.exit(1) }
}
