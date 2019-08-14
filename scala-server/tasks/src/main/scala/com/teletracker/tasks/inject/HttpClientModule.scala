package com.teletracker.tasks.inject

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.teletracker.common.http.HttpClient
import com.teletracker.tasks.http.Http4sClient
import com.twitter.inject.TwitterModule

class HttpClientModule extends TwitterModule {
  override protected def configure(): Unit = {
    install(
      new FactoryModuleBuilder()
        .implement(classOf[HttpClient], classOf[Http4sClient])
        .build(classOf[HttpClient.Factory])
    )
  }
}
