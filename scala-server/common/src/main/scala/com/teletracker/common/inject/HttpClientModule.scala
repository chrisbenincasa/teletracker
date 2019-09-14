package com.teletracker.common.inject

import com.google.inject.Key
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.teletracker.common.http.{BlockingHttp, Http4sClient, HttpClient}
import com.twitter.inject.TwitterModule

class BlockingHttpClientModule extends TwitterModule {
  override protected def configure(): Unit = {
    install(
      new FactoryModuleBuilder()
        .implement(
          classOf[HttpClient],
          classOf[Http4sClient]
        )
        .build(Key.get(classOf[HttpClient.Factory], classOf[BlockingHttp]))
    )
  }
}
