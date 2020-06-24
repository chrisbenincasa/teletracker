package com.teletracker.common.elasticsearch.scraping

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.{
  EsPotentialMatchItem,
  EsScrapedCastMember,
  EsScrapedCrewMember
}
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchCrud,
  ElasticsearchExecutor
}
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.HasId
import io.circe.{Codec, Encoder, Json}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EsScrapedItemStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchCrud[String, EsScrapedItemDoc]
    with ElasticsearchAccess {
  self =>

  override protected val indexName: String =
    teletrackerConfig.elasticsearch.scraped_items_index_name
}

object EsScrapedItemDoc {
  import com.teletracker.common.util.json.circe._
  import io.circe.syntax._

  implicit final val codec: Codec[EsScrapedItemDoc] =
    io.circe.generic.semiauto.deriveCodec

  implicit final val hasId: HasId.Aux[EsScrapedItemDoc, String] =
    HasId.instance(_.id)

  def fromAnyScrapedItem[T <: ScrapedItem: Encoder](
    scrapeItemType: String,
    version: Long,
    item: T
  ): EsScrapedItemDoc = {
    require(item.thingType.isDefined)
    require(item.externalId.isDefined)

    EsScrapedItemDoc(
      id = s"${scrapeItemType.toLowerCase}__${item.externalId.get}:${version}",
      `type` = scrapeItemType,
      version = version,
      availableDate = item.availableDate,
      title = item.title,
      releaseYear = item.releaseYear,
      network = item.network,
      status = item.status,
      externalId = item.externalId,
      description = item.description,
      itemType = item.thingType.get,
      url = item.url,
      posterImageUrl = item.posterImageUrl,
      cast = item.cast.map(
        _.map(
          c =>
            EsScrapedCastMember(name = c.name, order = c.order, role = c.role)
        )
      ),
      crew = item.crew.map(
        _.map(
          c =>
            EsScrapedCrewMember(name = c.name, order = c.order, role = c.role)
        )
      ),
      raw = item.asJson
    )
  }
}

case class EsScrapedItemDoc(
  id: String,
  `type`: String,
  version: Long,
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  status: String,
  externalId: Option[String],
  description: Option[String],
  itemType: ItemType,
  url: Option[String],
  posterImageUrl: Option[String],
  cast: Option[Seq[EsScrapedCastMember]],
  crew: Option[Seq[EsScrapedCrewMember]],
  raw: Json)
