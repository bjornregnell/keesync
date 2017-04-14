package keesync

class SyncServer(override val portNumber: Int) extends Server {
  override val inputPrompt = "keesync> "

  @annotation.tailrec
  private final def commandLoop(): Unit = scala.io.StdIn.readLine(inputPrompt) match {
    case "stop" => log("[Info] Godbye!"); sys.exit(0)
    case cmd    => log(s"[ERROR] Unkown command: $cmd"); commandLoop
  }

  def start(): Unit = {
    log("Server starting...")
    spawnAcceptLoop
    commandLoop
  }
}
