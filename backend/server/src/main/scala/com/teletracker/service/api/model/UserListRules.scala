package com.teletracker.service.api.model

import com.teletracker.common.db.model._
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Codec
import io.circe.generic.extras.Configuration
import java.util.UUID

object UserListRules {
  implicit val customConfig: Configuration =
    Configuration.default.withDiscriminator("type")

  import io.circe.shapes._
  import io.circe.generic.extras.semiauto._
  import io.circe.generic.extras.auto._
  import io.circe.generic.extras.Configuration

  implicit val codec: Codec[UserListRules] =
    deriveConfiguredCodec[UserListRules]

  def fromRow(dynamicListRules: DynamicListRules): UserListRules = {
    UserListRules(
      rules = dynamicListRules.rules.map(convertRule),
      sortOptions =
        dynamicListRules.sort.map(opts => UserListSortOptions(opts.sort))
    )
  }

  private def convertRule(dynamicListRule: DynamicListRule): UserListRule = {
    dynamicListRule match {
      case DynamicListPersonRule(personId, associationType, _) =>
        UserListPersonRule(Some(personId), None, associationType)

      case DynamicListTagRule(tagType, value, isPresent, _) =>
        UserListTagRule(tagType, value, isPresent)

      case DynamicListGenreRule(genreId, _) =>
        UserListGenreRule(genreId)

      case DynamicListItemTypeRule(itemType, _) =>
        UserListItemTypeRule(itemType)

      case DynamicListNetworkRule(networkId, _) =>
        UserListNetworkRule(networkId)

      case DynamicListReleaseYearRule(min, max, _) =>
        UserListReleaseYearRule(min, max)
    }
  }

  private def convertRule(trackedListRule: UserListRule): DynamicListRule = {
    trackedListRule match {
      case UserListPersonRule(personId, _, associationType) =>
        require(personId.isDefined)
        DynamicListPersonRule(personId.get, associationType)

      case UserListTagRule(tagType, value, isPresent) =>
        DynamicListTagRule(tagType, value, isPresent)

      case UserListGenreRule(genreId) =>
        DynamicListGenreRule(genreId)

      case UserListItemTypeRule(itemType) =>
        DynamicListItemTypeRule(itemType)

      case UserListNetworkRule(networkId) =>
        DynamicListNetworkRule(networkId)

      case UserListReleaseYearRule(minimum, maximum) =>
        DynamicListReleaseYearRule(minimum, maximum)
    }
  }
}

case class UserListRules(
  rules: List[UserListRule],
  sortOptions: Option[UserListSortOptions]) {
  require(rules.nonEmpty)
  def toRow: DynamicListRules = {
    DynamicListRules(
      rules = rules.map(UserListRules.convertRule),
      sort = sortOptions.map(opts => DynamicListDefaultSort(opts.sort))
    )
  }
}

case class UserListSortOptions(sort: String)

object UserListRule {
  implicit val customConfig: Configuration =
    Configuration.default.withDiscriminator("type")

  import io.circe.shapes._
  import io.circe.generic.extras.semiauto._
  import io.circe.generic.extras.auto._
  import io.circe.generic.extras.Configuration

  implicit val codec: Codec[UserListRule] = deriveConfiguredCodec[UserListRule]
}

sealed trait UserListRule

case class UserListTagRule(
  tagType: UserThingTagType,
  value: Option[Double],
  isPresent: Option[Boolean])
    extends UserListRule

case class UserListPersonRule(
  personId: Option[UUID],
  personSlug: Option[Slug],
  associationType: Option[PersonAssociationType])
    extends UserListRule

case class UserListGenreRule(genreId: Int) extends UserListRule

case class UserListItemTypeRule(itemType: ThingType) extends UserListRule

case class UserListNetworkRule(networkId: Int) extends UserListRule

case class UserListReleaseYearRule(
  minimum: Option[Int],
  maximum: Option[Int])
    extends UserListRule
