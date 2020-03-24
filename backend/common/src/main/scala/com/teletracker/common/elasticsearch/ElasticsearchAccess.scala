package com.teletracker.common.elasticsearch

import com.google.inject.Inject
import com.teletracker.common.db.dynamo.model.{
  StoredGenre,
  StoredNetwork,
  StoredUserList
}
import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  PersonAssociationType
}
import com.teletracker.common.db.{
  AddedTime,
  Bookmark,
  DefaultForListType,
  Popularity,
  Recent,
  SearchScore,
  SortMode
}
import io.circe.parser.decode
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.{
  BoolQueryBuilder,
  QueryBuilders,
  RangeQueryBuilder,
  TermQueryBuilder
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.{IdOrSlug, OpenDateRange}
import io.circe.Decoder
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.collection.JavaConverters._

trait ElasticsearchAccess {
//  @Inject private[this] var denormalizationCache: ItemDenormalizationCache = _

  private val logger = LoggerFactory.getLogger(getClass)

  protected def searchResponseToItems(
    response: SearchResponse
  ): ElasticsearchItemsResponse = {
    val hits = response.getHits

    val items = decodeSearchResponse[EsItem](response)

    // TODO: Hook this up fully
//    denormalizationCache.setBatch(
//      items
//        .flatMap(item => {
//          item.external_ids.toList.flatten.map(externalId => {
//            (
//              ExternalSource.fromString(externalId.provider),
//              externalId.id,
//              item.`type`
//            ) -> DenormalizationCacheItem(item.id, item.slug)
//          })
//        })
//        .toMap
//    )

    ElasticsearchItemsResponse(
      items.map(item => {
        item.copy(
          cast = item.cast.map(_.sortWith(EsOrdering.forItemCastMember))
        )
      }),
      hits.getTotalHits.value
    )
  }

  protected def searchResponseToPeople(
    response: SearchResponse
  ): ElasticsearchPeopleResponse = {
    val hits = response.getHits

    ElasticsearchPeopleResponse(
      decodeSearchResponse[EsPerson](response),
      hits.getTotalHits.value
    )
  }

  protected def searchResponseToUserItems(
    response: SearchResponse
  ): ElasticsearchUserItemsResponse = {
    val hits = response.getHits

    ElasticsearchUserItemsResponse(
      decodeSearchResponse[EsUserItem](response),
      hits.getTotalHits.value
    )
  }

  protected def decodeSearchResponse[T: Decoder: ClassTag](
    response: SearchResponse
  ): List[T] = {
    val hits = response.getHits
    val decodedHits = hits.getHits.flatMap(hit => {
      decodeSourceString[T](hit.getSourceAsString)
    })

    decodedHits.toList
  }

  final protected def decodeSourceString[T](
    source: String
  )(implicit decoder: Decoder[T],
    ct: ClassTag[T]
  ): Option[T] = {
    decode[T](source) match {
      case Left(value) =>
        logger.error(
          s"Couldn't decode ${ct.runtimeClass.getSimpleName} from source string:\n${source}",
          value
        )
        None

      case Right(value) => Some(value)
    }
  }

  protected def removeAdultItems(
    builder: BoolQueryBuilder
  ): BoolQueryBuilder = {
    builder.filter(
      QueryBuilders
        .boolQuery()
        .should(
          QueryBuilders
            .boolQuery()
            .mustNot(QueryBuilders.existsQuery("adult"))
        )
        .should(
          QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery("adult", false))
        )
        .minimumShouldMatch(1)
    )
  }

  protected def posterImageFilter(
    builder: BoolQueryBuilder
  ): BoolQueryBuilder = {
    builder.filter(
      QueryBuilders.termQuery("images.image_type", "poster")
    )
  }

  protected def genresFilter(
    builder: BoolQueryBuilder,
    genres: Set[StoredGenre]
  ): BoolQueryBuilder = {
    genreIdsFilter(builder, genres.map(_.id))
  }

  protected def genreIdsFilter(
    builder: BoolQueryBuilder,
    genreIds: Set[Int]
  ): BoolQueryBuilder = {
    require(genreIds.nonEmpty)
    builder.filter(
      genreIds
        .foldLeft(QueryBuilders.boolQuery())(
          (builder, genreId) =>
            builder.should(
              QueryBuilders.nestedQuery(
                "genres",
                QueryBuilders.termQuery("genres.id", genreId),
                ScoreMode.Avg
              )
            )
        )
        .minimumShouldMatch(1)
    )
  }

  protected def openDateRangeFilter(
    builder: BoolQueryBuilder,
    openDateRange: OpenDateRange
  ): BoolQueryBuilder = {
    require(openDateRange.isFinite)

    builder.filter(
      QueryBuilders
        .rangeQuery("release_date")
        .format("yyyy-MM-dd")
        .applyOptional(openDateRange.start)(
          (range, start) => range.gte(start.toString)
        )
        .applyOptional(openDateRange.end)(
          (range, end) => range.lte(end.toString)
        )
    )
  }

  protected def itemTypesFilter(
    builder: BoolQueryBuilder,
    itemTypes: Set[ItemType]
  ): BoolQueryBuilder = {
    require(itemTypes.nonEmpty)
    builder.filter(
      itemTypes.foldLeft(QueryBuilders.boolQuery())(itemTypeFilter)
    )
  }

  protected def itemTypeFilter(
    builder: BoolQueryBuilder,
    itemType: ItemType
  ) = {
    builder
      .should(QueryBuilders.termQuery("type", itemType.toString))
      .minimumShouldMatch(1)
  }

  protected def applyBookmark(
    builder: BoolQueryBuilder,
    bookmark: Bookmark,
    list: Option[StoredUserList],
    defaultSort: SortMode = Popularity() // Used when a bookmark passes in a "default" sort. Decided upon by the caller
  ): BoolQueryBuilder = {
    def applyRange(
      rangeBuilder: RangeQueryBuilder,
      desc: Boolean,
      value: Any
    ): BoolQueryBuilder = {
      (desc, bookmark.valueRefinement) match {
        case (true, Some(_)) =>
          builder.filter(rangeBuilder.lte(value))

        case (true, _) =>
          builder.filter(rangeBuilder.lt(value))

        case (false, Some(_)) =>
          builder.filter(
            rangeBuilder
              .gte(value)
          )

        case (false, _) =>
          builder.filter(
            rangeBuilder
              .gt(value)
          )
      }
    }

    @scala.annotation.tailrec
    def applyForSortMode(sortMode: SortMode): BoolQueryBuilder = {
      sortMode match {
        case SearchScore(_) =>
          builder

        case Popularity(desc) =>
          val baseQuery = QueryBuilders
            .rangeQuery("popularity")

          val popularity = bookmark.value.toDouble

          applyRange(baseQuery, desc, popularity)

        case Recent(desc) =>
          val baseQuery = QueryBuilders
            .rangeQuery("release_date")
          val releaseDate = bookmark.value

          applyRange(baseQuery, desc, releaseDate)

        case AddedTime(desc) => applyForSortMode(Recent(desc))

        case d @ DefaultForListType(_) if list.isDefined =>
          applyForSortMode(d.get(list.get.isDynamic))

        case DefaultForListType(_) => applyForSortMode(defaultSort)
      }
    }

    applyForSortMode(bookmark.sortMode)
      .applyIf(bookmark.valueRefinement.isDefined)(builder => {
        builder.filter(
          QueryBuilders.rangeQuery("id").gt(bookmark.valueRefinement.get)
        )
      })
  }

  @tailrec
  final protected def makeDefaultSort(
    sortMode: SortMode
  ): Option[FieldSortBuilder] = {
    sortMode match {
      case SearchScore(_) => None

      case Popularity(desc) =>
        Some(makeDefaultFieldSort("popularity", desc))

      case Recent(desc) =>
        Some(makeDefaultFieldSort("release_date", desc))

      case AddedTime(desc) =>
        makeDefaultSort(Recent(desc))

      case DefaultForListType(desc) =>
        makeDefaultSort(Popularity(desc))
    }
  }

  protected def makeDefaultFieldSort(
    field: String,
    desc: Boolean
  ): FieldSortBuilder = {
    new FieldSortBuilder(field)
      .order(if (desc) SortOrder.DESC else SortOrder.ASC)
      .missing("_last")
  }

  protected def peopleCreditSearchQuery(
    builder: BoolQueryBuilder,
    peopleCreditSearch: PeopleCreditSearch
  ): BoolQueryBuilder = {
    val cast = peopleCreditSearch.people.filter(
      _.associationType == PersonAssociationType.Cast
    )
    val crew = peopleCreditSearch.people.filter(
      _.associationType == PersonAssociationType.Crew
    )

    if (cast.isEmpty && crew.isEmpty) {
      builder
    } else {
      peopleCreditSearch.operator match {
        case BinaryOperator.Or =>
          builder.must(
            QueryBuilders
              .boolQuery()
              .minimumShouldMatch(1)
              .applyIf(cast.nonEmpty)(
                termsQueryForHasIdOrSlug(_, cast.map(_.personId), "cast")
              )
              .applyIf(crew.nonEmpty)(
                termsQueryForHasIdOrSlug(_, crew.map(_.personId), "crew")
              )
          )

        case BinaryOperator.And =>
          builder
            .through(b => {
              cast.foldLeft(b)(
                (currBuilder, credit) =>
                  currBuilder.filter(
                    QueryBuilders.nestedQuery(
                      "cast",
                      termQueryForHasIdOrSlug(credit.personId, "cast"),
                      ScoreMode.Avg
                    )
                  )
              )
            })
            .through(b => {
              crew.foldLeft(b)(
                (currBuilder, credit) =>
                  currBuilder.filter(
                    QueryBuilders.nestedQuery(
                      "crew",
                      termQueryForHasIdOrSlug(credit.personId, "crew"),
                      ScoreMode.Avg
                    )
                  )
              )
            })
      }
    }
  }

  protected def termsQueryForHasIdOrSlug(
    builder: BoolQueryBuilder,
    idOrSlugs: Seq[IdOrSlug],
    field: String
  ) = {
    val ids = idOrSlugs.flatMap(_.id)
    val slugs = idOrSlugs.flatMap(_.slug)
    builder
      .applyIf(ids.nonEmpty)(
        _.should(
          QueryBuilders.nestedQuery(
            field,
            QueryBuilders
              .termsQuery(
                s"$field.id",
                ids.map(_.toString).asJavaCollection
              ),
            ScoreMode.Avg
          )
        )
      )
      .applyIf(slugs.nonEmpty)(
        _.should(
          QueryBuilders.nestedQuery(
            field,
            QueryBuilders
              .termsQuery(
                s"$field.slug",
                slugs.map(_.value).asJavaCollection
              ),
            ScoreMode.Avg
          )
        )
      )
  }

  protected def termQueryForHasIdOrSlug(
    idOrSlug: IdOrSlug,
    field: String
  ): TermQueryBuilder = {
    idOrSlug match {
      case IdOrSlug(Left(id)) =>
        QueryBuilders
          .termQuery(
            s"$field.id",
            id.toString
          )
      case IdOrSlug(Right(slug)) =>
        QueryBuilders
          .termQuery(
            s"$field.slug",
            slug.value
          )
    }
  }

  protected def availabilityByNetworksOr(
    builder: BoolQueryBuilder,
    networks: Set[StoredNetwork]
  ) = {
    availabilityByNetworkIdsOr(builder, networks.map(_.id))
  }

  protected def availabilityByNetworkIdsOr(
    builder: BoolQueryBuilder,
    networks: Set[Int]
  ) = {
    builder.filter(
      networks.foldLeft(QueryBuilders.boolQuery())(availabilityByNetworkId)
    )
  }

  private def availabilityByNetworkId(
    builder: BoolQueryBuilder,
    networkId: Int
  ) = {
    builder
      .should(
        QueryBuilders.nestedQuery(
          "availability",
          QueryBuilders.termQuery("availability.network_id", networkId),
          ScoreMode.Avg
        )
      )
      .minimumShouldMatch(1)
  }
}
