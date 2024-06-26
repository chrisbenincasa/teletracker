package com.teletracker.common.util

import com.teletracker.common.db.model.ItemType
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class ListFilters(
  itemTypes: Option[Set[ItemType]],
  genres: Option[Set[Int]],
  networks: Option[Set[Int]],
  personIdentifiers: Option[Set[String]])

class ListFilterParser @Inject()(genreCache: GenreCache) {
  def parseListFilters(
    itemTypes: Option[Seq[String]],
    genres: Option[Seq[String]]
  )(implicit executionContext: ExecutionContext
  ): Future[ListFilters] = {
    val types = itemTypes match {
      case Some(Seq()) => Some(Set.empty[ItemType])
      case Some(value) =>
        Some(
          value.flatMap(typ => Try(ItemType.fromString(typ)).toOption).toSet
        )
      case None => None
    }

    val filteredGenreIdsFut = genres.map(_.map(HasGenreIdOrSlug.parse)) match {
      case Some(parsedGenres) if parsedGenres.exists(_.isRight) =>
        genreCache
          .get()
          .map(genresMap => {
            parsedGenres.flatMap {
              case Left(value) => Some(value)
              case Right(value) =>
                genresMap.find(_.slug == value).map(_.id)
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
        genres = filteredGenreIds,
        networks = None,
        personIdentifiers = None
      )
    })
  }
}
