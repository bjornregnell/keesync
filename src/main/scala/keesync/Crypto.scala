package keesync

import java.security.MessageDigest

object Crypto {
  import scala.util.Try

  object Base64 {
    import java.util.Base64.{getDecoder, getEncoder}
    def decodeToBytes(s: String): Array[Byte] = getDecoder.decode(s)
    def decodeToBytes(bytes: Array[Byte]): Array[Byte] = getDecoder.decode(bytes)
    def decodeToString(s: String): String = new String(decodeToBytes(s))
    def decodeToString(bytes: Array[Byte]): String = new String(decodeToBytes(bytes))

    def encodeToBytes(bytes: Array[Byte]): Array[Byte] = getEncoder.encode(bytes)
    def encodeToBytes(s: String): Array[Byte] = getEncoder.encode(s.getBytes)
    def encodeToString(bytes: Array[Byte]): String = getEncoder.encodeToString(bytes)
    def encodeToString(s: String): String = encodeToString(s.getBytes)
  }

  private lazy val rnd = new java.security.SecureRandom

  object Salt {
    val init: String = "wUliyZmCxzu1Ecmw7/BhC4Sfw7hr5V4+/0HwXWx08go="
    val saltLength = 32
    def next: String = {
      val xs = new Array[Byte](saltLength)
      rnd.nextBytes(xs)
      Base64.encodeToString(xs)
    }
  }

  object Password {
    def generate(length: Int = 20, charsToInclude: String = "0-9 A-Z a-z -!.*+/#<>"): String = {
      val chars: String = charsToInclude.split(' ').toSeq.map {
          case s if s.size == 3 && s(1) == '-' => (s(0) to s(2)).mkString
          case s => s
        }.mkString
      val xs: Seq[Char] = for (i <- 0 until length) yield { chars(rnd.nextInt(chars.size))}
      xs.mkString
    }
  }

  object Bytes {
    def toObject[T](bytes: Array[Byte]): T = {
      val bis = new java.io.ByteArrayInputStream(bytes)
      val ois = new java.io.ObjectInputStream(bis)
      try ois.readObject.asInstanceOf[T] finally ois.close
    }

    def fromObject[T](obj: T): Array[Byte] = {
      val bos = new java.io.ByteArrayOutputStream
      val oos = new java.io.ObjectOutputStream(bos)
      try {
        oos.writeObject(obj)
        bos.toByteArray
      } finally oos.close
    }
  }

  object SHA {
    val algorithm = "SHA-512"
    private[Crypto] val instance = java.security.MessageDigest.getInstance(algorithm)

    def hash(s: String): String =
      Base64.encodeToString(instance.digest(Base64.encodeToBytes(s)))

    def isValidPassword(password: String, salt: String, saltedHash: String): Boolean =
      hash(password + salt) == saltedHash
  }

  object AES { //https://en.wikipedia.org/wiki/Advanced_Encryption_Standard
    import javax.crypto.spec.SecretKeySpec
    import javax.crypto.{Cipher, SealedObject}

    val (algorithm, keyLength) = ("AES", 128)

    private def keySpec(password: String): SecretKeySpec = {
      val key = SHA.instance.digest(Base64.encodeToBytes(password)).take(keyLength/8)
      new SecretKeySpec(key, algorithm)
    }

    private def makeEncrypter(password: String): Cipher= {
      val enc = Cipher.getInstance(algorithm)
      enc.init(Cipher.ENCRYPT_MODE, keySpec(password))
      enc
    }

    private def makeDecrypter(password: String): Cipher= {
      val enc = Cipher.getInstance(algorithm)
      enc.init(Cipher.DECRYPT_MODE, keySpec(password))
      enc
    }

    def encryptSerializable(obj: java.io.Serializable, password: String): SealedObject =
      new SealedObject(obj, makeEncrypter(password))

    def decryptSealedObject[T](sealedObject: SealedObject, password: String): Option[T] =
      Try{ sealedObject.getObject(makeDecrypter(password)).asInstanceOf[T] }.toOption

    def encryptObjectToString[T](obj: T, password: String): String = {
      val bytes = Bytes.fromObject(obj)
      val b64 = Base64.encodeToString(bytes)
      val sealedObject = encryptSerializable(b64, password)
      val bytesOfSealed = Bytes.fromObject(sealedObject)
      val encrypted   = Base64.encodeToString(bytesOfSealed)
      encrypted
    }

    def decryptObjectFromString[T](encrypted: String, password: String): Option[T] =
      Try {
        val bytesOfSealed = Base64.decodeToBytes(encrypted)
        val sealedObject  = Bytes.toObject[SealedObject](bytesOfSealed)
        val b64 = decryptSealedObject[String](sealedObject, password).get
        val bytes = Base64.decodeToBytes(b64)
        val obj = Bytes.toObject[T](bytes)
        obj
      }.toOption

    def encryptString(secret: String, password: String): String = {
      val sealedObject = encryptSerializable(secret, password)
      Base64.encodeToString(Bytes.fromObject(sealedObject))
    }

    def decryptString(encrypted: String, password: String): Option[String] = Try {
      val bytes = Base64.decodeToBytes(encrypted)
      val sealedObject = Bytes.toObject[SealedObject](bytes)
      decryptSealedObject[String](sealedObject, password).get
    }.toOption
  }

  object RSA {
    import java.security.{PublicKey, PrivateKey, KeyPair}
    private val (algorithm, bitLength) = ("RSA", 2048)
    private val keyFactory = java.security.KeyFactory.getInstance(algorithm)

    private def generateNewKeyPair(): KeyPair = {
      val keyGen = java.security.KeyPairGenerator.getInstance(algorithm)
      keyGen.initialize(bitLength)
      keyGen.generateKeyPair
    }

    private def privateKeyFromString(privateKeyString: String): PrivateKey = {
      val bytes = java.util.Base64.getDecoder.decode(privateKeyString)
      val spec = new java.security.spec.PKCS8EncodedKeySpec(bytes)
      keyFactory.generatePrivate(spec)
    }

    private def publicKeyFromString(publicKeyString: String): PublicKey = {
      val bytes = java.util.Base64.getDecoder.decode(publicKeyString)
      val spec = new java.security.spec.X509EncodedKeySpec(bytes)
      keyFactory.generatePublic(spec)
    }

    private def publicKeyStringFromKeyPair(kp: KeyPair): String = {
      val bytes: Array[Byte] = kp.getPublic.getEncoded
      Base64.encodeToString(bytes)
    }

    private def privateKeyStringFromKeyPair(kp: KeyPair): String = {
      val bytes: Array[Byte] = kp.getPrivate.getEncoded
      Base64.encodeToString(bytes)
    }

    case class Keys(publicKey: String, privateKey: String)

    def generateKeys(): Keys = {
      val kp = generateNewKeyPair()
      Keys(publicKeyStringFromKeyPair(kp), privateKeyStringFromKeyPair(kp))
    }

    def decryptObject[T](encrypted: String, privateKey: String): T = {
      val key = privateKeyFromString(privateKey)
      val encryptedBytes = java.util.Base64.getDecoder.decode(encrypted)
      val cipher = javax.crypto.Cipher.getInstance(algorithm);
      cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key)
      val secretBytes = cipher.doFinal(encryptedBytes)
      Bytes.toObject[T](secretBytes)
    }

    def decryptString(encrypted: String, privateKey: String): String =
      decryptObject[String](encrypted, privateKey)

    def encryptObject[T](secret: T, publicKey: String): String = {
      val key = publicKeyFromString(publicKey)
      val secretBytes = Bytes.fromObject(secret)
      val cipher = javax.crypto.Cipher.getInstance(algorithm);
      cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
      val encryptedBytes = cipher.doFinal(secretBytes)
      java.util.Base64.getEncoder.encodeToString(encryptedBytes)
    }

    def encryptString(secret: String, publicKey: String): String =
      encryptObject[String](secret, publicKey)
  }

}
