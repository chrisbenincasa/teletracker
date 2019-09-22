package com.teletracker.common.crypto

import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.storage.StorageOptions

object BerglasDecodeTest extends App {
  val decoder = new BerglasDecoder(
    StorageOptions.getDefaultInstance.getService,
    KeyManagementServiceClient.create()
  )

  decoder.resolve("teletracker-secrets/tmdb-api-key-qa")
}
