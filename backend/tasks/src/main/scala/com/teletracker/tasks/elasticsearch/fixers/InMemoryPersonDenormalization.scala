package com.teletracker.tasks.elasticsearch.fixers

import cats.kernel.{Monoid, Semigroup}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsItemCastMember,
  EsItemCrewMember,
  EsPerson
}
import com.teletracker.common.model.tmdb.{
  CastMember,
  Movie,
  MovieCredits,
  TvShow,
  TvShowCredits
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, FileUtils, SourceRetriever}
import io.circe._
import io.circe.syntax._
import javax.inject.Inject
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Functions._
import com.twitter.util.StorageUnit
import io.circe.generic.JsonCodec
import java.net.URI
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class SqlItemLookup(location: URI) {
  private val connectionString = s"jdbc:sqlite:${location.getPath}"
  private lazy val connection = DriverManager.getConnection(connectionString)

  def createItemTable() = {
    val statement = connection.createStatement()
    Try {
      val result = statement.execute(
        """
          |CREATE TABLE IF NOT EXISTS items(
          | id integer NOT NULL,
          | type varchar NOT NULL,
          | json text NOT NULL,
          | PRIMARY KEY (id, type)
          |);
          |
          |""".stripMargin
      )

      if (statement.getWarnings ne null) {
        println(statement.getWarnings)
      }

      statement.close()

      result
    }
  }

  def insertRow(
    id: Int,
    typ: ItemType,
    json: String
  ) = {
    Try {
      val sql =
        """
          |INSERT INTO items(id, type, json) VALUES (?,?,?) ON CONFLICT(id,type) DO NOTHING;"
          |""".stripMargin

      val statement = connection.prepareStatement(sql)
      statement.setInt(1, id)
      statement.setString(2, typ.toString)
      statement.setString(3, json)
      statement.executeUpdate()
      statement.close()
    }
  }

  def getRow(
    id: Int,
    typ: ItemType
  ) = {
    val value = Try {
      val sql =
        """
          |SELECT json FROM items WHERE id = ? AND type = ? LIMIT 1;
          |""".stripMargin

      val statement = connection.prepareStatement(sql)
      statement.setInt(1, id)
      statement.setString(2, typ.toString)

      val results = statement.executeQuery()
      var res: Option[String] = None
      while (results.next()) {
        res = Option(results.getString("json"))
      }
      res
    } match {
      case Failure(exception) =>
        println("Got error")
        exception.printStackTrace()
        None
      case Success(value) => Some(value)
    }

    value.flatten
  }
}

class InMemoryPersonDenormalization @Inject()(
  teletrackerConfig: TeletrackerConfig,
  sourceRetriever: SourceRetriever,
  personLookup: PersonLookup,
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._
  import diffson.lcs._
  import io.circe._
  import io.circe.syntax._

  implicit private val lcs = new Patience[Json]

  private lazy val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val location = args.valueOrThrow[URI]("dbLocation")
    val moviesLocation = args.valueOrThrow[URI]("moviesLocation")
    val showsLocation = args.valueOrThrow[URI]("showsLocation")
    val personIdsFile = args.valueOrThrow[URI]("personIdsFile")
    val limit = args.valueOrDefault("limit", -1)
    val perFileLimit = args.valueOrDefault("perFileLimit", -1)
    val skipMovieImport = args.valueOrDefault("skipMovieImport", false)
    val skipShowImport = args.valueOrDefault("skipShowImport", false)
    val itemTypesToHandle = args.valueOrDefault(
      "itemTypesToHandle",
      Set(ItemType.Movie, ItemType.Show)
    )
    val personIdFilter = args.value[UUID]("personId")
    val personIdsOffset = args.valueOrDefault("personIdsOffset", 0)
    val append = args.valueOrDefault("append", false)

    val lookup = new SqlItemLookup(location)

    lookup.createItemTable() match {
      case Failure(exception) =>
        logger.error("Failed to create table", exception)
        throw exception
      case Success(value) =>
        logger.info(s"Successfully created table: $value")
    }

    if (!skipMovieImport) {
      sourceRetriever
        .getSourceStream(moviesLocation)
        .foreach(
          source => {
            try {
              new IngestJobParser()
                .stream[Movie](source.getLines())
                .collect {
                  case Right(value) => value
                }
                .foreach(value => {
                  lookup
                    .insertRow(
                      value.id,
                      ItemType.Movie,
                      value.asJson.noSpaces
                    ) match {
                    case Failure(exception) =>
                      logger.error("Couldn't insert", exception)
                    case Success(value) =>
                  }
                })
            } finally {
              source.close()
            }
          }
        )
    }

    if (!skipShowImport) {
      sourceRetriever
        .getSourceStream(showsLocation)
        .foreach(
          source => {
            try {
              new IngestJobParser()
                .stream[TvShow](source.getLines())
                .collect {
                  case Right(value) => value
                }
                .foreach(value => {
                  lookup
                    .insertRow(
                      value.id,
                      ItemType.Show,
                      value.asJson.noSpaces
                    ) match {
                    case Failure(exception) =>
                      logger.error("Couldn't insert", exception)
                    case Success(value) =>
                  }
                })
            } finally {
              source.close()
            }
          }
        )
    }

    val rotater = FileRotator.everyNBytes(
      "cast-crew-updates",
      StorageUnit.fromMegabytes(10),
      Some("cast-crew-updates"),
      append = append
    )

    if (personIdFilter.isDefined) {
      handlePerson(personIdFilter.get, lookup, itemTypesToHandle, rotater)
    } else {
      sourceRetriever
        .getSourceStream(personIdsFile)
        .safeTake(limit)
        .foreach(source => {
          try {
            AsyncStream
              .fromStream(source.getLines().toStream.drop(personIdsOffset))
              .safeTake(perFileLimit)
              .map(UUID.fromString)
              .grouped(10)
              .delayedForeachF(1 second, scheduler)(group => {
                Future
                  .sequence(
                    group
                      .map(handlePerson(_, lookup, itemTypesToHandle, rotater))
                  )
                  .map(_ => {})
              })
              .await()
          } finally {
            source.close()
          }
        })
    }
  }

  private def handlePerson(
    personId: UUID,
    sqlLookup: SqlItemLookup,
    itemTypes: Set[ItemType],
    rotater: FileRotator
  ) = {
    logger.info(s"Handling person: ${personId}")
    personLookup
      .lookupPerson(
        Left(personId),
        materializeCredits = false,
        creditsLimit = None
      )
      .flatMap {
        case Some((person, _)) =>
          val castItemIds = person.cast_credits.getOrElse(Nil).map(_.id)
          val crewItemIds = person.crew_credits.getOrElse(Nil).map(_.id)

          itemLookup
            .lookupItemsByIds((castItemIds ++ crewItemIds).toSet)
            .map(itemsById => {
              val castItems = getIdsToLookup(castItemIds.toSet, itemsById)
              val crewItems = getIdsToLookup(crewItemIds.toSet, itemsById)

              (castItems ++ crewItems)
                .groupBy(_.id)
                .mapValues(_.head)
                .values
                .filter(item => itemTypes(item.`type`))
            })
            .map(items => {
              items.foreach(item => {
                item.externalIdsGrouped
                  .get(ExternalSource.TheMovieDb)
                  .map(_.toInt)
                  .flatMap(tmdbId => {
                    sqlLookup
                      .getRow(tmdbId, item.`type`)
                      .flatMap {
                        case value if item.`type` == ItemType.Movie =>
                          io.circe.parser
                            .decode[Movie](value)
                            .toOption
                            .flatMap(rawMovie => {
                              rawMovie.credits
                                .flatMap(handleRawTvCredits(person, item, _))
                            })
                        case value if item.`type` == ItemType.Show =>
                          io.circe.parser
                            .decode[TvShow](value)
                            .toOption
                            .flatMap(rawShow => {
                              rawShow.credits
                                .flatMap(handleRawTvCredits(person, item, _))
                            })
                      }
                  })
                  .foreach(updatedItem => {
                    if (item != updatedItem) {
//                      logger.info(
//                        s"Would've updated id = ${item.id}:\n${diff(item.asJson, updatedItem.asJson).asJson.spaces2}"
//                      )
                      val cast = updatedItem.cast.asJson
                      val crew = updatedItem.crew.asJson
                      val update = Map(
                        "doc" -> Map(
                          "cast" -> updatedItem.cast.map(_.asJson),
                          "crew" -> updatedItem.crew.map(_.asJson)
                        ).collect {
                          case (key, Some(value)) => key -> value
                        }.asJson
                      ).asJson.noSpaces
                      val bulkUpdate = EsBulkUpdate(
                        teletrackerConfig.elasticsearch.items_index_name,
                        updatedItem.id,
                        update
                      )
                      rotater.writeLines(bulkUpdate.lines)
                      logger
                        .info(s"Generated update for item = ${updatedItem.id}")
//                      logger.info(s"${update}")
                    }
                  })
              })
            })
        case None =>
          logger.error(s"Could not find person with id = ${personId}")
          Future.unit
      }
  }

  private def getIdsToLookup(
    itemIds: Set[UUID],
    itemsById: Map[UUID, Option[EsItem]]
  ) = {
    val foundItems = itemsById.collect {
      case (id, Some(item)) if itemIds.contains(id) => item
    }
    val missingItems = itemIds -- foundItems.map(_.id).toSet

    if (missingItems.nonEmpty) {
      logger
        .warn(s"Did not find the following items: ${missingItems
          .mkString("(", ", ", ")")}")
    }

    foundItems
  }

  private def handleRawTvCredits(
    person: EsPerson,
    item: EsItem,
    credits: MovieCredits
  ) = {
    person.externalIdsGrouped
      .get(ExternalSource.TheMovieDb)
      .map(_.toInt)
      .map(personId => {
        val castMember = credits.cast.getOrElse(Nil).find(_.id == personId)
        updateCastAndCrew(person, item, castMember)
      })
  }

  private def handleRawTvCredits(
    person: EsPerson,
    item: EsItem,
    credits: TvShowCredits
  ) = {
    person.externalIdsGrouped
      .get(ExternalSource.TheMovieDb)
      .map(_.toInt)
      .map(personId => {
        val castMember = credits.cast.getOrElse(Nil).find(_.id == personId)
        updateCastAndCrew(person, item, castMember)
      })
  }

  private def updateCastAndCrew(
    person: EsPerson,
    item: EsItem,
    rawCastMember: Option[CastMember]
  ) = {
    val newCast =
      person.cast_credits.getOrElse(Nil).find(_.id == item.id) match {
        case Some(member) =>
          val newCastMember = EsItemCastMember(
            character = member.character,
            id = person.id,
            order = rawCastMember
              .flatMap(_.order)
              .getOrElse(item.cast.getOrElse(Nil).size),
            name = person.name.getOrElse(""),
            slug = person.slug
          )

          item.cast match {
            case Some(existingCast) =>
              Some(
                existingCast.filterNot(_.id == person.id) :+ newCastMember
              )
            case None =>
              Some(List(newCastMember))
          }
        case None => item.cast
      }

    val newCrew =
      person.crew_credits.getOrElse(Nil).find(_.id == item.id) match {
        case Some(member) =>
          val newCrewMember = EsItemCrewMember(
            department = member.department,
            id = person.id,
            name = person.name.getOrElse(""),
            slug = person.slug,
            job = member.job,
            order = None
          )

          item.crew match {
            case Some(existingCrew) =>
              Some(
                existingCrew.filterNot(_.id == person.id) :+ newCrewMember
              )
            case None =>
              Some(List(newCrewMember))
          }
        case None => item.crew
      }

    item.copy(
      cast = newCast.map(_.sortWith(EsOrdering.forItemCastMember)),
      crew = newCrew.map(_.sortWith(EsOrdering.forItemCrewMember))
    )
  }
}

class CombineMovieUpdates @Inject()(
  sourceRetriever: SourceRetriever,
  fileUtils: FileUtils,
  teletrackerConfig: TeletrackerConfig)
    extends TeletrackerTaskWithDefaultArgs {
  import cats.implicits._
  import io.circe.parser._

  override protected def runInternal(args: Args): Unit = {
    val missingIdsUri = args.valueOrThrow[URI]("missingIdsInput")
    val updatesUri = args.valueOrThrow[URI]("updatesInput")

    val ids = fileUtils.readAllLinesToSet(missingIdsUri)
    val castCrewUpdatesById =
      new mutable.HashMap[String, List[CastCrewUpdate]]()

    val rotater = FileRotator.everyNBytes(
      "cast-crew-updates-filtered",
      StorageUnit.fromMegabytes(10),
      Some("cast-crew-updates-filtered")
    )

    sourceRetriever
      .getSourceStream(updatesUri)
      .foreach(source => {
        try {
          source.getLines().grouped(2).map(_.toList).foreach {
            case lines @ op :: doc :: Nil =>
              decode[EsBulkUpdateRaw](op).foreach(update => {
                if (ids.contains(update.update._id)) {
                  decode[DocUpdate[CastCrewUpdate]](doc).foreach(ccupdate => {
                    val existing =
                      castCrewUpdatesById.getOrElse(update.update._id, Nil)
                    castCrewUpdatesById += (update.update._id -> (existing :+ ccupdate.doc))
                  })
                } else {
                  decode[DocUpdate[CastCrewUpdate]](doc).foreach(ccupdate => {
                    val updated = ccupdate.doc.copy(
                      cast = ccupdate.doc.cast.filter(_.nonEmpty),
                      crew = ccupdate.doc.crew.filter(_.nonEmpty)
                    )

                    rotater.writeLines(
                      Seq(
                        op,
                        Map("doc" -> updated.asJson.dropNullValues).asJson.dropNullValues.noSpaces
                      )
                    )
                  })
                }
              })
            case _ =>
          }
        } finally {
          source.close()
        }
      })

    val combined = castCrewUpdatesById.toList.map {
      case (key, updates) => key -> updates.combineAll
    }

    combined.foreach {
      case (id, updates) =>
        val update = Map(
          "doc" -> Map(
            "cast" -> updates.cast
              .filter(_.nonEmpty)
              .map(_.asJson.dropNullValues),
            "crew" -> updates.crew
              .filter(_.nonEmpty)
              .map(_.asJson.dropNullValues)
          ).collect {
            case (key, Some(value)) => key -> value
          }.asJson
        ).asJson.noSpaces
        val bulkUpdate = EsBulkUpdate(
          teletrackerConfig.elasticsearch.items_index_name,
          UUID.fromString(id),
          update
        )
        rotater.writeLines(bulkUpdate.lines)
    }
  }

}

@JsonCodec
case class DocUpdate[T](doc: T)

object CastCrewUpdate {
  import cats.implicits._

  implicit val codec: Codec[CastCrewUpdate] =
    io.circe.generic.semiauto.deriveCodec

  implicit val monoid: Monoid[CastCrewUpdate] = new Monoid[CastCrewUpdate] {
    override def empty: CastCrewUpdate =
      CastCrewUpdate(cast = None, crew = None)

    override def combine(
      x: CastCrewUpdate,
      y: CastCrewUpdate
    ): CastCrewUpdate = {
      val leftCast = x.cast.getOrElse(Nil)
      val rightCast = y.cast.getOrElse(Nil)
      val combinedCast = leftCast.groupBy(_.id) |+| rightCast.groupBy(_.id)
      val newCast = combinedCast.values.flatMap(_.headOption)

      val leftCrew = x.crew.getOrElse(Nil)
      val rightCrew = y.crew.getOrElse(Nil)
      val combinedCrew = leftCrew.groupBy(_.id) |+| rightCrew.groupBy(_.id)
      val newCrew = combinedCrew.values.flatMap(_.headOption)

      CastCrewUpdate(
        cast =
          if (newCast.isEmpty) None
          else Some(newCast.toList.sortWith(EsOrdering.forItemCastMember)),
        crew =
          if (newCrew.isEmpty) None
          else Some(newCrew.toList.sortWith(EsOrdering.forItemCrewMember))
      )
    }
  }
}

@JsonCodec
case class CastCrewUpdate(
  cast: Option[List[EsItemCastMember]],
  crew: Option[List[EsItemCrewMember]])
