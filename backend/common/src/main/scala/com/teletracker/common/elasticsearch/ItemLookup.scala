package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.cache.ExternalIdMappingCache
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{EsExternalId, EsItem}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Maps._
import com.teletracker.common.util.Slug
import javax.inject.Inject
import org.elasticsearch.action.get.{GetRequest, MultiGetRequest}
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.{BoolQueryBuilder, Operator, QueryBuilders}
import org.elasticsearch.index.query.QueryBuilders.{
  boolQuery,
  matchQuery,
  termQuery
}
import org.elasticsearch.indices.TermsLookup
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ItemLookup @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor,
  idMappingLookup: ElasticsearchExternalIdMappingStore,
  externalIdMappingCache: ExternalIdMappingCache
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  def lookupItemsByIds(ids: Set[UUID]): Future[Map[UUID, Option[EsItem]]] = {
    if (ids.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val multiGetRequest = new MultiGetRequest()
      ids.toList
        .map(
          id =>
            new MultiGetRequest.Item(
              teletrackerConfig.elasticsearch.items_index_name,
              id.toString
            )
        )
        .foreach(multiGetRequest.add)

      elasticsearchExecutor
        .multiGet(multiGetRequest)
        .map(response => {
          response.getResponses.toList
            .flatMap(response => {
              val id = response.getId
              if (response.isFailed) {
                None
              } else {
                Some(
                  UUID.fromString(id) -> decodeSourceString[EsItem](
                    response.getResponse.getSourceAsString
                  )
                )
              }
            })
            .toMap
        })
    }
  }

  def lookupItemByExternalId(
    source: ExternalSource,
    id: String,
    thingType: ItemType,
    materializeRecommendations: Boolean = false,
    materializeCredits: Boolean = false
  ): Future[Option[EsItem]] = {
    val externalId = EsExternalId(source, id)

    def lookupId(id: UUID): Future[Option[EsItem]] = {
      lookupItem(
        Left(id),
        None,
        shouldMaterializeRecommendations = materializeRecommendations,
        shouldMateralizeCredits = materializeCredits
      ).map(_.map(_.rawItem))
    }

    def searchForId(): Future[Option[EsItem]] = {
      val query = boolQuery()
        .filter(
          termQuery("external_ids", externalId.toString)
        )
        .filter(termQuery("type", thingType.toString))

      singleItemSearch(query)
    }

    externalIdMappingCache.get(externalId, thingType) match {
      case Some(value) => lookupId(value)

      case None =>
        idMappingLookup
          .getItemIdForExternalId(externalId, thingType)
          .flatMap {
            case Some(value) => lookupId(value)
            case None        => searchForId()
          }
    }
  }

  def lookupItemsByExternalIds(
    items: List[(ExternalSource, String, ItemType)]
  ): Future[Map[(EsExternalId, ItemType), EsItem]] = {
    if (items.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val keys = items.map {
        case (source, str, itemType) => EsExternalId(source, str) -> itemType
      }.toSet

      val foundCachedMappings = externalIdMappingCache.getAll(keys)
      val missingMappings = keys -- foundCachedMappings.keySet

      val storeMappingsFut = if (missingMappings.nonEmpty) {
        idMappingLookup
          .getItemIdsForExternalIds(missingMappings)
      } else {
        Future.successful(Map.empty[(EsExternalId, ItemType), UUID])
      }

      val searchFallbackResultsFut = storeMappingsFut.flatMap(storeMappings => {
        val stillMissingMappings = missingMappings -- storeMappings.keySet

        if (stillMissingMappings.nonEmpty) {
          val searches = stillMissingMappings.map {
            case (externalId, typ) =>
              val query = boolQuery()
                .filter(
                  termQuery("external_ids", externalId.toString)
                )
                .filter(termQuery("type", typ.toString))

              new SearchRequest(
                teletrackerConfig.elasticsearch.items_index_name
              ).source(new SearchSourceBuilder().query(query).size(1))
          }

          val multiReq = new MultiSearchRequest()
          searches.foreach(multiReq.add)

          elasticsearchExecutor
            .multiSearch(multiReq)
            .map(resp => {
              resp.getResponses.toList
                .zip(items.map(item => item._1 -> item._2))
                .map {
                  case (response, sourceAndId) =>
                    searchResponseToItems(response.getResponse).items.headOption
                      .map(sourceAndId -> _)
                }
            })
            .map(_.flatten.toMap)
        } else {
          Future.successful(Map.empty[(ExternalSource, String), EsItem])
        }
      })

      val lookResultsFut = storeMappingsFut.flatMap(storeMappings => {
        val allKnownMappings = foundCachedMappings ++ storeMappings
        val externalIdsById = allKnownMappings.reverse
        lookupItemsByIds(allKnownMappings.values.toSet).map(foundItems => {
          foundItems
            .collect {
              case (uuid, Some(item)) =>
                externalIdsById.get(uuid).map(_.map(_ -> item))
            }
            .flatten
            .foldLeft(Map.empty[(EsExternalId, ItemType), EsItem])(_ ++ _)
        })
      })

      for {
        lookupResults <- lookResultsFut
        searchFallbacks <- searchFallbackResultsFut
      } yield {
        lookupResults ++ searchFallbacks.map {
          case ((source, id), item) =>
            (EsExternalId(source, id), item.`type`) -> item
        }
      }
    }
  }

  def lookupFuzzy(
    fuzzyRequests: List[FuzzyItemLookupRequest]
  ): Future[Map[String, EsItem]] = {
    if (fuzzyRequests.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val searches = fuzzyRequests
        .map(request => {
          fuzzyMatchQuery(
            title = request.title,
            thingType = request.itemType,
            description = request.description,
            releaseYear = request.releaseYearRange,
            looseYearMatching = request.looseReleaseYearMatching
          )
        })
        .map(query => {
          val searchSource = new SearchSourceBuilder().query(query).size(1)
          new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
            .source(searchSource)
        })

      val multiReq = new MultiSearchRequest()
      searches.foreach(multiReq.add)

      elasticsearchExecutor
        .multiSearch(multiReq)
        .map(resp => {
          resp.getResponses.toList.zip(fuzzyRequests.map(_.title)).map {
            case (response, title) =>
              searchResponseToItems(response.getResponse).items.headOption
                .map(title -> _)
          }
        })
        .map(_.flatten.toMap)
    }

  }

  def lookupItemsBySlug(
    slugs: List[(Slug, ItemType, Option[Range])]
  ): Future[Map[Slug, EsItem]] = {
    if (slugs.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val searches = slugs
        .map(Function.tupled(slugMatchQuery))
        .map(query => {
          val searchSource = new SearchSourceBuilder().query(query).size(1)
          new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
            .source(searchSource)
        })

      val multiReq = new MultiSearchRequest()
      searches.foreach(multiReq.add)

      elasticsearchExecutor
        .multiSearch(multiReq)
        .map(resp => {
          resp.getResponses.toList.zip(slugs.map(_._1)).map {
            case (response, title) =>
              searchResponseToItems(response.getResponse).items.headOption
                .map(title -> _)
          }
        })
        .map(_.flatten.toMap)
    }
  }

  def lookupItemBySlug(
    slug: Slug,
    thingType: ItemType,
    releaseYear: Option[Range]
  ): Future[Option[EsItem]] = {
    singleItemSearch(slugMatchQuery(slug, thingType, releaseYear))
  }

  def lookupItem(
    identifier: Either[UUID, Slug],
    thingType: Option[ItemType],
    shouldMaterializeRecommendations: Boolean = true,
    shouldMateralizeCredits: Boolean = true
  ): Future[Option[ItemLookupResponse]] = {
    val identifierQuery = identifier match {
      case Left(value) =>
        boolQuery()
          .filter(termQuery("id", value.toString))

      case Right(value) =>
        require(thingType.isDefined)

        boolQuery()
          .filter(termQuery("slug", value.toString))
    }

    val query = identifierQuery.applyOptional(thingType)(
      (builder, typ) =>
        builder.filter(
          termQuery("type", typ.toString)
        )
    )

    val search = new SearchRequest()
      .source(new SearchSourceBuilder().query(query).size(1))

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToItems)
      .map(response => {
        response.items.headOption
      })
      .flatMap {
        case None =>
          Future.successful(None)
        case Some(item) =>
          val recommendationsOrder =
            item.recommendations.getOrElse(Nil).map(_.id).zipWithIndex.toMap

          val recsFut =
            if (shouldMaterializeRecommendations) {
              materializeRecommendations(item.id)
            } else {
              Future.successful(ElasticsearchItemsResponse.empty)
            }

          val castFut =
            if (shouldMateralizeCredits) {
              lookupItemCredits(item.id, item.cast.getOrElse(Nil).size)
            } else {
              Future.successful(ElasticsearchPeopleResponse.empty)
            }

          for {
            recs <- recsFut
            cast <- castFut
          } yield {
            Some(
              ItemLookupResponse(
                item,
                cast,
                recs.copy(
                  items = recs.items.sortBy(
                    i => recommendationsOrder.getOrElse(i.id, Int.MaxValue)
                  )
                )
              )
            )
          }
      }
  }

  private def slugMatchQuery(
    slug: Slug,
    thingType: ItemType,
    releaseYear: Option[Range]
  ) = {
    boolQuery()
      .filter(
        termQuery("slug", slug.value)
      )
      .filter(termQuery("type", thingType.toString))
      .applyOptional(releaseYear)(
        releaseYearRangeQuery(_, _, looseMatching = false)
      )
  }

  private def fuzzyMatchQuery(
    title: String,
    thingType: Option[ItemType],
    description: Option[String],
    releaseYear: Option[Range],
    looseYearMatching: Boolean
  ) = {
    boolQuery()
      .must(
        boolQuery()
          .should(
            matchQuery("original_title", title)
              .operator(Operator.AND)
          )
          .should(
            matchQuery("title", title).operator(Operator.AND).boost(2.0f)
          )
          .should(
            matchQuery("alternative_titles.title", title)
              .operator(Operator.AND)
          )
          .minimumShouldMatch(1)
      )
      .applyOptional(description)(
        (builder, desc) =>
          builder.should(
            matchQuery("overview", desc).boost(1.5f)
          )
      )
      .applyOptional(thingType)(
        (builder, typ) =>
          builder.filter(
            termQuery("type", typ.toString).boost(1.5f)
          )
      )
      .applyOptional(releaseYear)(
        releaseYearRangeQuery(_, _, looseYearMatching)
      )
  }

  private def releaseYearRangeQuery(
    builder: BoolQueryBuilder,
    range: Range,
    looseMatching: Boolean
  ) = {
    val rangeQuery = QueryBuilders
      .rangeQuery("release_date")
      .format("yyyy")
      .gte(s"${range.head}||/y")
      .lte(s"${range.last}||/y")

    if (looseMatching) {
      builder.should(rangeQuery).boost(2)
    } else {
      builder.filter(
        rangeQuery
      )
    }
  }

  private def singleItemSearch(query: BoolQueryBuilder) = {
    val searchSource = new SearchSourceBuilder().query(query).size(1)

    elasticsearchExecutor
      .search(
        new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
          .source(searchSource)
      )
      .map(searchResponseToItems)
      .map(_.items.headOption)
  }

  private def materializeRecommendations(
    id: UUID
  ): Future[ElasticsearchItemsResponse] = {
    val query = boolQuery()
      .must(
        QueryBuilders
          .termsLookupQuery(
            "id",
            new TermsLookup(
              teletrackerConfig.elasticsearch.items_index_name,
              id.toString,
              "recommendations.id"
            )
          )
      )

    val search =
      new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
        .source(
          new SearchSourceBuilder()
            .query(query)
            .size(
              20
            )
        )

    elasticsearchExecutor.search(search).map(searchResponseToItems)
  }

  private def lookupItemCredits(
    id: UUID,
    limit: Int
  )(implicit executionContext: ExecutionContext
  ): Future[ElasticsearchPeopleResponse] = {
    val query = boolQuery()
      .must(
        QueryBuilders
          .termsLookupQuery(
            "id",
            new TermsLookup(
              teletrackerConfig.elasticsearch.items_index_name,
              id.toString,
              "cast.id"
            )
          )
      )

    val search =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(
          new SearchSourceBuilder()
            .fetchSource(
              null,
              Array("biography", "cast_credits", "crew_credits")
            )
            .query(query)
            .size(limit)
        )

    elasticsearchExecutor.search(search).map(searchResponseToPeople)
  }
}

case class ItemLookupResponse(
  rawItem: EsItem,
  materializedCast: ElasticsearchPeopleResponse,
  materializedRecommendations: ElasticsearchItemsResponse)

case class FuzzyItemLookupRequest(
  title: String,
  description: Option[String],
  itemType: Option[ItemType],
  releaseYearRange: Option[Range],
  looseReleaseYearMatching: Boolean)
