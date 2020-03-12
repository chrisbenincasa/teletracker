package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.scraper.{IngestJobArgsLike, ScrapedItem}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchLookup @Inject()(
  itemSearch: ItemLookup,
  elasticsearchLookupBySlug: ElasticsearchLookupBySlug,
  elasticsearchExactTitleLookup: ElasticsearchExactTitleLookup
)(implicit executionContext: ExecutionContext)
    extends MatchMode {

  override def lookup[T <: ScrapedItem](
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    LookupMethod.unravel(
      args,
      List(
        elasticsearchLookupBySlug,
        elasticsearchExactTitleLookup
      ).map(_.toMethod[T]),
      items
    )
  }
}