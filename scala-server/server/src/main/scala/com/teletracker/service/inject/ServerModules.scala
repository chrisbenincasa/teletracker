package com.teletracker.service.inject

import com.google.inject.{Key, Module}
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.teletracker.common.http.{BlockingHttp, Http4sClient, HttpClient}
import com.teletracker.common.inject.Modules
import com.teletracker.service.http.FinagleHttpClient
import com.twitter.inject.TwitterModule
import scala.concurrent.ExecutionContext

object ServerModules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] =
    Modules() ++ Seq(
      new HttpClientModule
    )
}

class HttpClientModule extends TwitterModule {
  override protected def configure(): Unit = {
    install(
      new FactoryModuleBuilder()
        .implement(classOf[HttpClient], classOf[FinagleHttpClient])
        .implement(
          classOf[HttpClient],
          classOf[BlockingHttp],
          classOf[Http4sClient]
        )
        .build(classOf[HttpClient.Factory])
    )

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
