package keesync

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import java.net.Socket

class Client(val host: String = "localhost", val port: Int) {

  val cmdDelayMillis: Long = 50L
  val cmdPrompt: String ="> "

  var userIdOpt:   Option[String] = None
  var passwrodOpt: Option[String] = None

  def hello() =
    Terminal.put("Welcome to keesync Client to test the keehive SyncServer!")

  @volatile private var connectionOpt: Option[Network.Connection] = None

  def connect() = {
    val sock = new Socket(host, port)
    connectionOpt = Some(Network.Connection.fromSocket(sock))
    Terminal.put(s"Connected to $host:$port")
  }

  def receive(): String = connectionOpt.get.read

  def expect(prefix: String): Either[String, String] = {
    val msg = receive()
    if (msg.startsWith(prefix)) Right(msg.stripPrefix(prefix))
    else Left(msg)
  }

  def send(msg: String): Unit = connectionOpt.get.write(msg)

  def connectionFailed() = Try {
    Terminal.put("Connection failed!")
    connectionOpt.get.close
    connectionOpt = None
  }

/*  def keepConnected: Unit = par.spawnLoop {
    while (!isConnected) connect match {
      case Success(_) => isConnected = true
      case Failure(_) =>
        connectionFailed
        println("Retrying to connect...")
    }
    Thread.sleep(retryTime)
  }*/

  def spawnReceiveLoop() = Future {
    while (true) {
      val msg = receive()
      Terminal.put(s"\n$msg")
    }
  }

  def cmdLoop(): Unit = {
    while (true) {
      val cmd = Terminal.get(cmdPrompt)
      send(cmd)
      Thread.sleep(cmdDelayMillis);
    }
  }

  def login(): Unit = {
    val uid = Terminal.get(      "Enter keesync  user id: ")
    val pwd = Terminal.getSecret("Enter keesync password: ")
    scala.util.Try {
      send(s"login:$uid")
      expect("key:") match {
        case Right(key) =>
          println(s"Key received from server: $key")
          val encryptedPwd = Crypto.RSA.encryptString(secret = pwd, publicKey = key)
          send(s"pwd:$encryptedPwd")
          expect("auth:") match {
            case Right("OK") =>
              println(s"Authentication OK!")
            case _ =>
              println(s"Authentication failed: wrong password.")
              System.exit(1)
          }

        case Left(somethingElse) =>
          println(s"Expected key from server :( but got this instead: $somethingElse")
          System.exit(1)
      }
    } recover {
      case e: java.io.EOFException =>
        println(s"Login failed: connection closed by server.")
        System.exit(1)
      case e: Throwable =>
        println(s"Login failed: ${e.getMessage}")
        System.exit(1)
    }
  }

  def start(): Unit = {
    Terminal.put("Attempting to connect to server...")
    //keepConnected()
    connect()
    Terminal.put("Connected to server. Attempting login...")
    login()
    spawnReceiveLoop()
    cmdLoop()
  }
}
