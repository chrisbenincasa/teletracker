package com.teletracker.common.util

import com.teletracker.common.db.model.ThingType
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class ListFilters(
  itemTypes: Option[Set[ThingType]],
  genres: Option[Set[Int]])

class ListFilterParser @Inject()(genreCache: GenreCache) {
  def parseListFilters(
    itemTypes: Seq[String],
    genres: Option[Seq[String]]
  )(implicit executionContext: ExecutionContext
  ): Future[ListFilters] = {
    val types = if (itemTypes.nonEmpty) {
      Some(
        itemTypes
          .flatMap(typ => Try(ThingType.fromString(typ)).toOption)
          .toSet
      )
    } else {
      None
    }

    val filteredGenreIdsFut = genres.map(_.map(HasGenreIdOrSlug.parse)) match {
      case Some(parsedGenres) if parsedGenres.exists(_.isRight) =>
        genreCache
          .get()
          .map(genresMap => {
            parsedGenres.flatMap {
              case Left(value) => Some(value)
              case Right(value) =>
                genresMap.values.find(_.slug == value).flatMap(_.id)
            }
          })
          .map(_.toSet)
          .map(Some(_))

      case Some(parsedGenres) =>
        Future.successful(Some(parsedGenres.flatMap(_.left.toOption).toSet))

      case None =>
        Future.successful(None)
    }

    filteredGenreIdsFut.map(filteredGenreIds => {
      ListFilters(
        itemTypes = types,
        genres = filteredGenreIds
      )
    })
  }
}
