package keesync

case class Credential (
  userId: String,
  salt: String,
  saltedHash: String,
  keys: Crypto.RSA.Keys = Crypto.RSA.generateKeys,
  lastLoginOpt: Option[Long] = None,
  created: Long = System.currentTimeMillis
){
  final val MillisPerDay: Long = (1000 * 60 * 60 * 24).toLong
  def ageCreatedMillis: Long = System.currentTimeMillis - created
  def ageCreatedDays: Int = (ageCreatedMillis / MillisPerDay).toInt
  def updateLastLogin: Credential = copy(lastLoginOpt=Some(System.currentTimeMillis))

  def isValidPassword(password: String): Boolean =
    saltedHash == Crypto.SHA.hash(password + salt)
}

object Credential {
  def create(userId: String, password: String) = {
    val salt = Crypto.Salt.next
    new Credential(userId, salt, Crypto.SHA.hash(password + salt))
  }

}
