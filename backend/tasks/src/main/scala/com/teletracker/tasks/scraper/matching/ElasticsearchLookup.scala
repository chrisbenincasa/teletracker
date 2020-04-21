package com.teletracker.tasks.scraper.matching

import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.scraper.{IngestJobArgsLike, ScrapedItem}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchLookup @Inject()(
  elasticsearchLookupBySlug: ElasticsearchLookupBySlug,
  elasticsearchExactTitleLookup: ElasticsearchExactTitleLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod.Agnostic {

  override def toMethod[T <: ScrapedItem]: LookupMethod[T] = {
    new CustomElasticsearchLookup[T](
      List(elasticsearchLookupBySlug, elasticsearchExactTitleLookup)
        .map(_.toMethod[T])
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
