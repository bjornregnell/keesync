package keesync

object Vault {
  case class Master(salt: String = "", saltedHash: String = "")
  val username: String = System.getProperty("user.name")
  final val mpwFileName   = s"$username-mpw.txt"
  final val vaultFileName = s"$username-vlt.txt"

  def saveMasterPassword(file: String, mpw: String): String = {
    val salt = Crypto.Salt.next
    val m = Master(salt, Crypto.SHA.hash(mpw + salt))
    val encrypted = Crypto.AES.encryptObjectToString(m, mpw)
    Disk.saveString(encrypted, file)
    salt
  }

  case class MasterCheck(isValid: Boolean, isCreated: Boolean, salt: String = "")

  def checkMasterPassword(file: String, mpw: String): MasterCheck = {
    if (Disk.isExisting(file)) {
      val encrypted = Disk.loadString(file)
      val Master(salt, saltedHash) =
        Crypto.AES.decryptObjectFromString[Master](encrypted, mpw).getOrElse(Master())
      if (Crypto.SHA.hash(mpw + salt) == saltedHash)
        MasterCheck(isValid = true, isCreated = false, salt)
      else MasterCheck(isValid = false, isCreated = false)
    } else {
      Disk.createFileIfNotExist(file)
      val salt = saveMasterPassword(file, mpw)
      MasterCheck(isValid = true, isCreated = true, salt)
    }
  }

  case class Result(valtOpt: Option[Vault], isCreated: Boolean)

  def open(masterPassword: String, path: String): Result = {
    val mpwFile   = s"$path/$mpwFileName"
    val vaultFile = s"$path/$vaultFileName"
    val MasterCheck(isValid, isCreated, salt) =
      checkMasterPassword(mpwFile, masterPassword)
    if (isValid) {
      val vault = new Vault(masterPassword, mpwFile, vaultFile, salt)
      Result(Some(vault), isCreated)
    } else Result(None, isCreated = false)
  }
}

class Vault private (
        initMasterPassword: String,
        masterPasswordFile: String,
        vaultFile:          String,
        private var salt:   String){

  import scala.collection.mutable

  type Secrets = mutable.Map[String, Credential]

  private var mpw = initMasterPassword
  private var secrets: Secrets = loadSecrets()
  private def key = mpw + salt

  private def saveSecrets(secrets: Secrets): Unit = {
    val encrypted = Crypto.AES.encryptObjectToString(secrets, key)
    Terminal.put(s"Saving ${secrets.size} user credentials in vault.")
    Disk.saveString(encrypted, vaultFile)
  }

  private def loadSecrets(): Secrets = {
    if (Disk.isExisting(vaultFile)) {
      val encrypted = Disk.loadString(vaultFile)
      val secretsOpt: Option[Secrets] = Crypto.AES.decryptObjectFromString(encrypted, key)
      if (secretsOpt.isEmpty)
        Main.abort("Inconsistency between master password and encrypted vault!")
      Terminal.put(s"Loaded ${secretsOpt.get.size} secrets.")
      secretsOpt.get
    } else {
      val emptySecrets: Secrets = mutable.Map.empty
      Terminal.put(s"Creating new empty vault.")
      saveSecrets(emptySecrets)
      emptySecrets
    }
  }

  def toMap: Map[String, Credential] = secrets.toMap
  def apply(key: String): Credential = secrets(key)
  def get(key: String): Option[Credential] = secrets.get(key)

  def size: Int = secrets.size

  def add(creds: Credential*): Int = {
    creds.foreach(cred => secrets += (cred.userId -> cred))
    saveSecrets(secrets)
    secrets.size
  }

  def update(newCred: Credential): Unit = {
    secrets(newCred.userId) = newCred
    saveSecrets(secrets)
  }

  def removeUser(userId: String): Unit = {
    secrets.remove(userId)
    saveSecrets(secrets)
  }

  def isUserExisting(userId: String): Boolean = secrets.isDefinedAt(userId)

  override def toString: String = s"Vault(\n$toMap\n)"

}
