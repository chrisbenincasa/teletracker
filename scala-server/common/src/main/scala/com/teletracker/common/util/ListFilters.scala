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
    genres: Seq[String]
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

    val parsedGenres = genres.map(HasGenreIdOrSlug.parse)
    val filteredGenreIdsFut = if (parsedGenres.exists(_.isRight)) {
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
    } else {
      Future.successful(parsedGenres.flatMap(_.left.toOption).toSet)
    }

    filteredGenreIdsFut.map(filteredGenreIds => {
      ListFilters(
        itemTypes = types,
        genres = if (filteredGenreIds.isEmpty) None else Some(filteredGenreIds)
      )
    })
  }
}
