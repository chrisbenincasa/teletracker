package com.teletracker.service.api.model

import com.teletracker.common.db.model._
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

  implicit val codec: Codec[UserListRules] = deriveCodec[UserListRules]

  def fromRow(dynamicListRules: DynamicListRules): UserListRules = {
    UserListRules(
      rules = dynamicListRules.rules.map(convertRule)
    )
  }

  private def convertRule(dynamicListRule: DynamicListRule): UserListRule = {
    dynamicListRule match {
      case DynamicListPersonRule(personId, _) =>
        UserListPersonRule(personId)

      case DynamicListTagRule(tagType, value, isPresent, _) =>
        UserListTagRule(tagType, value, isPresent)
    }
  }

  private def convertRule(trackedListRule: UserListRule): DynamicListRule = {
    trackedListRule match {
      case UserListPersonRule(personId) =>
        DynamicListPersonRule(personId)

      case UserListTagRule(tagType, value, isPresent) =>
        DynamicListTagRule(tagType, value, isPresent)
    }
  }
}

case class UserListRules(rules: List[UserListRule]) {
  require(rules.nonEmpty)
  def toRow: DynamicListRules = {
    DynamicListRules(
      rules = rules.map(UserListRules.convertRule)
    )
  }
}

object UserListRule {
  implicit val customConfig: Configuration =
    Configuration.default.withDiscriminator("type")

  import io.circe.shapes._
  import io.circe.generic.extras.semiauto._
  import io.circe.generic.extras.auto._
  import io.circe.generic.extras.Configuration

  implicit val codec: Codec[UserListRule] = deriveCodec[UserListRule]
}

sealed trait UserListRule

case class UserListTagRule(
  tagType: UserThingTagType,
  value: Option[Double],
  isPresent: Option[Boolean])
    extends UserListRule

case class UserListPersonRule(personId: UUID) extends UserListRule
