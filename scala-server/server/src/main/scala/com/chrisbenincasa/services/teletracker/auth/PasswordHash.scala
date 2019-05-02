package com.chrisbenincasa.services.teletracker.auth

import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHash {
  val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
  // The following constants may be changed without breaking existing hashes.
  val SALT_BYTES = 24
  val HASH_BYTES = 24
  val PBKDF2_ITERATIONS = 1000
  val ITERATION_INDEX = 0
  val SALT_INDEX = 1
  val PBKDF2_INDEX = 2

  /**
   * Returns a salted PBKDF2 hash of the password.
   *
   * @param password the password to hash
   * @return a salted PBKDF2 hash of the password
   */
  def createHash(password: String): String = createHash(password.toCharArray)

  def createHash(password: Array[Char]): String = { // Generate a random salt
    val random = new SecureRandom()
    val salt = new Array[Byte](SALT_BYTES)
    random.nextBytes(salt)
    // Hash the password
    val hash = pbkdf2(password, salt, PBKDF2_ITERATIONS, HASH_BYTES)
    // format iterations:salt:hash
    PBKDF2_ITERATIONS + ":" + toHex(salt) + ":" + toHex(hash)
  }

  /**
   * Validates a password using a hash.
   *
   * @param password the password to check
   * @param goodHash the hash of the valid password
   * @return true if the password is correct, false if not
   */
  def validatePassword(password: String, goodHash: String): Boolean = validatePassword(password.toCharArray, goodHash)

  def validatePassword(password: Array[Char], goodHash: String): Boolean = { // Decode the hash into its parameters
    val params = goodHash.split(":")
    val iterations = params(ITERATION_INDEX).toInt
    val salt = fromHex(params(SALT_INDEX))
    val hash = fromHex(params(PBKDF2_INDEX))
    // Compute the hash of the provided password, using the same salt,
    // iteration count, and hash length
    val testHash = pbkdf2(password, salt, iterations, hash.length)
    // Compare the hashes in constant time. The password is correct if
    // both hashes match.
    slowEquals(hash, testHash)
  }

  /**
   * Compares two byte arrays in length-constant time. This comparison method
   * is used so that password hashes cannot be extracted from an on-line
   * system using a timing attack and then attacked off-line.
   *
   * @param a the first byte array
   * @param b the second byte array
   * @return true if both byte arrays are the same, false if not
   */
  private def slowEquals(a: Array[Byte], b: Array[Byte]) = {
    var diff = a.length ^ b.length
    var i = 0
    while (i < a.length && i < b.length) {
      diff = diff | (a(i) ^ b(i))
      i += 1
    }

    diff == 0
  }

  /**
   * Computes the PBKDF2 hash of a password.
   *
   * @param password   the password to hash.
   * @param salt       the salt
   * @param iterations the iteration count (slowness factor)
   * @param bytes      the length of the hash to compute in bytes
   * @return the PBDKF2 hash of the password
   */
  private def pbkdf2(password: Array[Char], salt: Array[Byte], iterations: Int, bytes: Int) = {
    val spec = new PBEKeySpec(password, salt, iterations, bytes * 8)
    val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
    skf.generateSecret(spec).getEncoded
  }

  /**
   * Converts a string of hexadecimal characters into a byte array.
   *
   * @param hex the hex string
   * @return the hex string decoded into a byte array
   */
  private def fromHex(hex: String) = {
    val binary = new Array[Byte](hex.length / 2)
    var i = 0
    while (i < binary.length) {
      binary(i) = Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16).toByte
      i += 1
    }
    binary
  }

  /**
   * Converts a byte array into a hexadecimal string.
   *
   * @param array the byte array to convert
   * @return a length*2 character string encoding the byte array
   */
  private def toHex(array: Array[Byte]) = {
    val bi = new BigInteger(1, array)
    val hex = bi.toString(16)
    val paddingLength = (array.length * 2) - hex.length
    if (paddingLength > 0) ("%0" + paddingLength + "d").format(0) + hex
    else hex
  }
}