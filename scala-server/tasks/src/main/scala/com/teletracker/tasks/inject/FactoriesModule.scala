package com.teletracker.tasks.inject

import com.teletracker.tasks.scraper.ElasticsearchFallbackMatcher
import com.twitter.inject.TwitterModule

class FactoriesModule extends TwitterModule {
  override protected def configure(): Unit = {
    bindAssistedFactory[ElasticsearchFallbackMatcher.Factory]
  }
}
