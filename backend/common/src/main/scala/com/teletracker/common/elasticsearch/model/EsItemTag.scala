package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.UserThingTagType
import com.teletracker.common.elasticsearch.model.EsItemTag.TagFormatter
import io.circe.Codec
import java.time.Instant
import java.util.UUID
import scala.util.Try

object EsItemTaggable {
  implicit val esItemTaggableString: EsItemTaggable[String] =
    new EsItemTaggable[String] {
      override def makeItemTag(
        tag: String,
        value: Option[String],
        lastUpdated: Instant
      ) = {
        EsItemTag(
          tag = tag,
          value = None,
          string_value = value,
          last_updated = Some(lastUpdated)
        )
      }

      override def makeUserItemTag(
        userId: String,
        itemId: UUID,
        tag: UserThingTagType,
        value: Option[String],
        lastUpdated: Instant = Instant.now()
      ): EsUserItemTag = {
        EsUserItemTag(
          tag = tag.toString,
          user_id = Some(userId),
          item_id = Some(itemId.toString),
          int_value = None,
          double_value = None,
          date_value = None,
          string_value = value,
          last_updated = Some(lastUpdated)
        )
      }
    }

  implicit val esItemTaggableDouble: EsItemTaggable[Double] = {
    new EsItemTaggable[Double] {
      override def makeItemTag(
        tag: String,
        value: Option[Double],
        lastUpdated: Instant
      ): EsItemTag = EsItemTag(
        tag = tag,
        value = value,
        string_value = None,
        last_updated = Some(lastUpdated)
      )

      override def makeUserItemTag(
        userId: String,
        itemId: UUID,
        tag: UserThingTagType,
        value: Option[Double],
        lastUpdated: Instant
      ): EsUserItemTag =
        EsUserItemTag(
          tag = tag.toString,
          user_id = Some(userId),
          item_id = Some(itemId.toString),
          int_value = None,
          double_value = value,
          date_value = None,
          string_value = None,
          last_updated = Some(lastUpdated)
        )
    }
  }

  implicit val esItemTaggableInt: EsItemTaggable[Int] =
    new EsItemTaggable[Int] {
      override def makeItemTag(
        tag: String,
        value: Option[Int],
        lastUpdated: Instant
      ): EsItemTag = {
        EsItemTag(
          tag = tag,
          value = value.map(_.toDouble),
          string_value = None,
          last_updated = Some(lastUpdated)
        )
      }

      override def makeUserItemTag(
        userId: String,
        itemId: UUID,
        tag: UserThingTagType,
        value: Option[Int],
        lastUpdated: Instant
      ): EsUserItemTag = {
        EsUserItemTag(
          tag = tag.toString,
          user_id = Some(userId),
          item_id = Some(itemId.toString),
          int_value = value,
          double_value = None,
          date_value = None,
          string_value = None,
          last_updated = Some(lastUpdated)
        )
      }
    }
}

trait EsItemTaggable[T] {
  def makeItemTag(
    tag: String,
    value: Option[T],
    lastUpdated: Instant = Instant.now()
  ): EsItemTag

  def makeUserScopedTag(
    userId: String,
    tag: UserThingTagType,
    value: Option[T],
    lastUpdated: Instant = Instant.now()
  ) = {
    makeItemTag(TagFormatter.format(userId, tag), value, lastUpdated)
  }

  def makeUserItemTag(
    userId: String,
    itemId: UUID,
    tag: UserThingTagType,
    value: Option[T],
    lastUpdated: Instant = Instant.now()
  ): EsUserItemTag
}

object EsItemTag {
  import io.circe.generic.semiauto._
  implicit val esItemTagCodec: Codec[EsItemTag] = deriveCodec

  final val SEPARATOR = "__"

  object TagFormatter {
    def format(
      userId: String,
      tag: UserThingTagType
    ): String = {
      s"${userId}${SEPARATOR}${tag}"
    }
  }

  //  def userScoped(
  //    userId: String,
  //    tag: UserThingTagType,
  //    value: Option[Double],
  //    lastUpdated: Option[Instant]
  //  ): EsItemTag = {
  //    EsItemTag(TagFormatter.format(userId, tag), value, None, lastUpdated)
  //  }

  def userScopedString(
    userId: String,
    tag: UserThingTagType,
    value: Option[String],
    lastUpdated: Option[Instant]
  ): EsItemTag = {
    EsItemTag(TagFormatter.format(userId, tag), None, value, lastUpdated)
  }

  def userScoped[T](
    userId: String,
    tag: UserThingTagType,
    value: Option[T],
    lastUpdated: Option[Instant]
  )(implicit esItemTaggable: EsItemTaggable[T]
  ): EsItemTag = {
    esItemTaggable.makeUserScopedTag(
      userId,
      tag,
      value,
      lastUpdated.getOrElse(Instant.now())
    )
  }

  object UserScoped {
    def unapply(arg: EsItemTag): Option[
      (
        String,
        UserThingTagType,
        Option[Double],
        Option[String],
        Option[Instant]
      )
    ] = {
      arg.tag.split(SEPARATOR, 2) match {
        case Array(userId, tag) =>
          Try(UserThingTagType.fromString(tag)).toOption
            .map(
              tagType =>
                (userId, tagType, arg.value, arg.string_value, arg.last_updated)
            )
        case _ => None
      }
    }
  }
}

case class EsItemTag(
  tag: String,
  value: Option[Double],
  string_value: Option[String],
  last_updated: Option[Instant])
