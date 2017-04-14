package keesync

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import java.net.Socket

class Client(val port: Int, val host: String = "localhost") {

  val cmdDelayMillis: Long = 50L
  val cmdPrompt: String ="> "

  def hello() =
    println("Welcome to keesync Client to test the keehive SyncServer!")

  @volatile private var connectionOpt: Option[Network.Connection] = None

  def connect() = {
    val sock = new Socket(host, port)
    connectionOpt = Some(Network.Connection.fromSocket(sock))
    println(s"Connected to $host:$port")
  }

  def receive(): String = connectionOpt.get.read

  def send(msg: String): Unit = connectionOpt.get.write(msg)

  def connectionFailed() = Try {
    println("Connection failed!")
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
      println(s"\n$msg")
    }
  }

  def cmdLoop(): Unit = {
    while (true) {
      val cmd = scala.io.StdIn.readLine(cmdPrompt)
      send(cmd)
      Thread.sleep(cmdDelayMillis);
      //println(s"msg sent: $cmd")
    }
  }

  def start(): Unit = {
    println("Attempting to connect to server...")
    //keepConnected()
    connect()
    spawnReceiveLoop()
    cmdLoop()
  }
}
