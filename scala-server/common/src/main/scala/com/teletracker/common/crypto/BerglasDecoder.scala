package com.teletracker.common.crypto

import com.google.cloud.kms.v1.{DecryptRequest, KeyManagementServiceClient}
import com.google.cloud.storage.{BlobId, Storage}
import com.google.protobuf.ByteString
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, KeyGenerator}
import javax.inject.Inject
import java.nio.charset.Charset
import java.util.Base64
import scala.reflect.api

object BerglasDecoder {
  object KnownSecrets {
    final val SecretPrefix = "teletracker-secrets"
    final val TmdbApiKey = "tmdb-api-key-qa"
  }

  def makePath(name: String) = KnownSecrets.SecretPrefix + "/" + name
}

class BerglasDecoder @Inject()(
  storage: Storage,
  keyManagementServiceClient: KeyManagementServiceClient) {

  def resolve(secretName: String): String = {

    val Array(bucket, objectName) = secretName.split("/", 2)

    val file = storage.get(BlobId.of(bucket, objectName))

    val berglasMetadata = Option(file.getMetadata.get("berglas-kms-key"))

    val key = berglasMetadata.getOrElse(throw new RuntimeException)

    val Array(content1, content2) =
      new String(file.getContent(), Charset.defaultCharset()).split(":")

    val encDek = Base64.getDecoder.decode(content1)
    val cipherText = Base64.getDecoder.decode(content2)

    val decryptResponse = keyManagementServiceClient.decrypt(
      DecryptRequest
        .newBuilder()
        .setName(key)
        .setCiphertext(ByteString.copyFrom(encDek))
        .setAdditionalAuthenticatedData(
          ByteString.copyFrom(objectName.getBytes())
        )
        .build()
    )

    decipher(decryptResponse.getPlaintext.toByteArray, cipherText)
  }

  private def decipher(
    dek: Array[Byte],
    cipherText: Array[Byte]
  ): String = {
    val (nonce, toDecrypt) = cipherText.splitAt(12)

    val keyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
      Cipher.DECRYPT_MODE,
      new SecretKeySpec(dek, "AES"),
      new GCMParameterSpec(16 * 8, nonce)
    )

    val (txt, tag) = toDecrypt.splitAt(toDecrypt.length - 16)
    cipher.update(txt)
    val decrypted = cipher.doFinal(tag)

    new String(decrypted)
  }
}
