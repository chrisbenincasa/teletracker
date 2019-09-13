package com.teletracker.common.crypto

import javax.inject.Inject
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import java.util.Base64

object BerglasDecoder {
  object KnownSecrets {
    final val SecretPrefix = "teletracker-secrets"
    final val TmdbApiKey = "tmdb-api-key-qa"
  }

  def makePath(name: String) = KnownSecrets.SecretPrefix + "/" + name
}

class BerglasDecoder @Inject()() {
  // TODO inject
  val kmsClient = KmsClient.create()
  val s3 = S3Client.create()
  val ssm = SsmClient.create()

  def resolve(secretName: String): String = {
//    val Array(bucket, objectName) = secretName.split("/", 2)

    ssm
      .getParameter(
        GetParameterRequest
          .builder()
          .name(secretName)
          .withDecryption(true)
          .build()
      )
      .parameter()
      .value()

//    response
//
//    kmsClient
//      .decrypt(
//        DecryptRequest
//          .builder()
//          .ciphertextBlob(SdkBytes.fromByteArray(bytes))
//          .build()
//      )
//      .plaintext()
//      .asUtf8String()
  }

//  def resolve(secretName: String): String = {
//
//    val Array(bucket, objectName) = secretName.split("/", 2)
//
//    val file = storage.get(BlobId.of(bucket, objectName))
//
//    val berglasMetadata = Option(file.getMetadata.get("berglas-kms-key"))
//
//    val key = berglasMetadata.getOrElse(throw new RuntimeException)
//
//    val Array(content1, content2) =
//      new String(file.getContent(), Charset.defaultCharset()).split(":")
//
//    val encDek = Base64.getDecoder.decode(content1)
//    val cipherText = Base64.getDecoder.decode(content2)
//
//    val decryptResponse = keyManagementServiceClient.decrypt(
//      DecryptRequest
//        .newBuilder()
//        .setName(key)
//        .setCiphertext(ByteString.copyFrom(encDek))
//        .setAdditionalAuthenticatedData(
//          ByteString.copyFrom(objectName.getBytes())
//        )
//        .build()
//    )
//
//    decipher(decryptResponse.getPlaintext.toByteArray, cipherText)
//  }

//  private def decipher(
//    dek: Array[Byte],
//    cipherText: Array[Byte]
//  ): String = {
//    val (nonce, toDecrypt) = cipherText.splitAt(12)
//
//    val keyGenerator = KeyGenerator.getInstance("AES")
//    keyGenerator.init(256)
//
//    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
//    cipher.init(
//      Cipher.DECRYPT_MODE,
//      new SecretKeySpec(dek, "AES"),
//      new GCMParameterSpec(16 * 8, nonce)
//    )
//
//    val (txt, tag) = toDecrypt.splitAt(toDecrypt.length - 16)
//    cipher.update(txt)
//    val decrypted = cipher.doFinal(tag)
//
//    new String(decrypted)
//  }
}
