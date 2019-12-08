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

class BerglasDecoder @Inject()(s3: S3Client) {
  // TODO inject
  private val ssm = SsmClient.create()

  def resolve(secretName: String): String = {
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
  }
}
