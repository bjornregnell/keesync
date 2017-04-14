package keesync

object Network {
  import java.io.{DataInputStream, DataOutputStream}
  import java.io.{BufferedInputStream, BufferedOutputStream}
  import java.net.Socket

  def streamsFromSocket(s: Socket): (DataInputStream, DataOutputStream) = (
    new DataInputStream(new BufferedInputStream(s.getInputStream)),
    new DataOutputStream(new BufferedOutputStream(s.getOutputStream)))

  def writeAndFlush(dos: DataOutputStream, msg: String): Unit = {
    dos.writeUTF(msg)
    dos.flush()
  }

  case class Channel(sock: Socket, dis: DataInputStream, dos: DataOutputStream){
    def read: String = dis.readUTF
    def write(msg: String): Unit = writeAndFlush(dos, msg)
    def close(): Unit = { sock.close; dis.close; dos.close }
  }
  object Channel {
    def fromSocket(socket: Socket): Channel = {
      val (dis, dos) = streamsFromSocket(socket)
      new Channel(socket, dis, dos)
    }
  }
}
