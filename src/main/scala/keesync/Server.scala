package keesync

class Server(val port: Int) {
  import scala.concurrent.ExecutionContext.Implicits._
  import scala.concurrent.Future
  import scala.util.{Try , Success, Failure}

  trait Message { def msg : String }
  case class Request (msg: String) extends Message
  case class Response(msg: String) extends Message
  type Dispatcher = Request => Response

  private def timestamp(): String = (new java.util.Date).toString

  def outputPrompt: String = s"[$timestamp]"

  lazy val serverPort = Network.openServerPort(port)

  def log(msg: String) = println(s"\n$outputPrompt $msg")
  def startMsg(): Unit = log(s"Server started, port: $port")

  protected def spawnAcceptLoop() =  Future {  // par.spawnLoop
    while (true) {
      val connection = Network.Connection.toClient(from = serverPort)
      log(s"[INFO] New connection created: $connection")
      spawnLoginAndThenLoop(connection)
    }
  } onFailure { case e: Throwable => log(s"[ERROR] Accept loop terminated: ${e.getMessage()}") }

  def expectPrefix(connection: Network.Connection, prefix: String): Either[String, String] = {
    val msg = connection.read
    if (msg.startsWith(prefix)) Right(msg.stripPrefix(prefix))
    else Left(msg)
  }

  private def spawnLoginAndThenLoop(connection: Network.Connection) =  {

    def loopUntilException(): Unit = {
      while (true) {
        val msg = connection.read
        log(s"[Info] Got message '$msg'")
        connection.write(s"Echo: '$msg'")
      }
    }

    Future {
      expectPrefix(connection, "login:") match {

        case Right(uid) =>

          log(s"login requested, checking user: $uid")
          if (vault.isUserExisting(uid)) {
            val key = vault(uid).keys.publicKey
            connection.write(s"key:$key")
            expectPrefix(connection, "pwd:") match {
              case Right(encrPwd) =>
                val pkey = vault(uid).keys.privateKey
                val pwd = Crypto.RSA.decryptString(encrypted=encrPwd, privateKey=pkey)
                if (vault(uid).isValidPassword(pwd)) {
                  log(s"[info] pwd ok for user $uid")
                  connection.write("auth:OK")
                  loopUntilException()
                } else {
                  log(s"[ALERT] Invalid pwd from user $uid.")
                  Thread.sleep(1000)
                  connection.close
                }

              case Left(somethingElse) =>
                log(s"[ALERT] Malformed pwd attempt: $somethingElse")
                connection.close
            }
          } else {
            log(s"[ALERT] Failed login attempt, non-existing user: $uid")
            connection.close
          }

        case Left(somethingElse) =>

          log(s"[ALERT] Malformed login attempt: $somethingElse")
          connection.close

      }
    } onFailure { case e: Throwable => log(s"[ERROR] Read loop terminated: ${e.getMessage()}") }
  }

  val inputPrompt = "keesync> "

  var vault: Vault = _

  val helpText: String = """
    |stop       shut down this Server
    |addUser    add new user
    |listUsers  list all existing users
    |?          print this message
    """.trim.stripMargin
  val commands = helpText.split('\n').toSeq.map(_.trim.takeWhile(_.isLetter)).filter(_.nonEmpty)

  @annotation.tailrec
  private final def commandLoop(): Unit = {
    Terminal.get(inputPrompt) match {
      case "stop" => log("[info] Godbye!"); sys.exit(0)

      case "?" => println(helpText)

      case cmd if cmd.startsWith("addUser") =>
        val uid = Terminal.get(s"[input] Enter new userId: ")
        val pwd = {
          val maybeEmpty = Terminal.get(s"[input] Enter new password (empty to generate): ")
          if (maybeEmpty.isEmpty)
            Crypto.Password.generate(length=6, charsToInclude="0-9 a-z")
          else maybeEmpty
        }
        vault.add(Credential.create(uid, pwd))
        println(s"[ACTION] Write down password and send to new user: $pwd")

        case cmd if cmd.startsWith("listUsers") =>
          log(s"[info] Credentials in vault:\n$vault")

      case cmd  if cmd.nonEmpty  => log(s"[ERROR] Unkown command: $cmd    Type ? for help.")
      case _  =>
    }
    commandLoop
  }

  def notifyMpwCreated(): Unit = log("[info] New master password file created.")
  def notifyMpwGood(): Unit = log("[info] Server vault with credentials is open!")

  def start(): Unit = {
    val mpw = Terminal.getSecret("\n[input] Enter server password for credentials crypto:\n" )
    val Vault.Result(vaultOpt, isCreated) = Vault.open(mpw, Main.path)
    if (vaultOpt.isDefined) {
      vault = vaultOpt.get
      if (isCreated) notifyMpwCreated() else notifyMpwGood()
    } else Main.abort("Bad password.")

    log(s"[info] Server starting at: $serverPort")
    spawnAcceptLoop()
    Terminal.setCompletions(commands, Seq())
    commandLoop()
  }

}
