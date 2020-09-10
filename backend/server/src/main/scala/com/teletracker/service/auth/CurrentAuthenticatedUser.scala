package com.teletracker.service.auth

object KnownUserGroups {
  final val Admins = "admins"
}

case class CurrentAuthenticatedUser(
  userId: String,
  groups: Option[Set[String]]) {

  def isAdmin: Boolean = groups.exists(_.contains(KnownUserGroups.Admins))
}
