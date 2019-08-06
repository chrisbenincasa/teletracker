package com.teletracker.service.inject

import com.google.inject.Module
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.teletracker.common.http.HttpClient
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
        .build(classOf[HttpClient.Factory])
    )
  }
}
