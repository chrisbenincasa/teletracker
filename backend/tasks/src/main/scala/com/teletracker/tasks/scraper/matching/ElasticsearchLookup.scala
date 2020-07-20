package com.teletracker.tasks.scraper.matching

import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.tasks.scraper.IngestJobArgsLike
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchLookup @Inject()(
  elasticsearchExactTitleLookup: ElasticsearchExactTitleLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod.Agnostic {

  override def create[T <: ScrapedItem]: LookupMethod[T] = {
    new CustomElasticsearchLookup[T](
      List(elasticsearchExactTitleLookup)
        .map(_.create[T])
    )
  }
}

class CustomElasticsearchLookup[T <: ScrapedItem](
  lookups: List[LookupMethod[T]]
)(implicit executionContext: ExecutionContext)
    extends LookupMethod[T] {
  override def apply(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    LookupMethod.unravel(args, lookups, items)
  }
}
