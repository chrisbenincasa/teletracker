package com.teletracker.service.inject

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.{Key, Module}
import com.teletracker.common.http.{BlockingHttp, Http4sClient, HttpClient}
import com.teletracker.common.inject.Modules
import com.teletracker.service.auth.CurrentAuthenticatedUser
import com.teletracker.service.http.FinagleHttpClient
import com.twitter.inject.TwitterModule
import com.twitter.inject.requestscope.RequestScopeBinding
import scala.concurrent.ExecutionContext

object ServerModules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] =
    Modules() ++ Seq(
      new HttpClientModule,
      new CurrentAuthenticatedUserModule
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

class CurrentAuthenticatedUserModule
    extends TwitterModule
    with RequestScopeBinding {
  override protected def configure(): Unit = {
    bindRequestScope[Option[CurrentAuthenticatedUser]]
  }
}
