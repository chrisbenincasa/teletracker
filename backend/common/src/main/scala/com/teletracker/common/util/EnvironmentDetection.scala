package com.teletracker.common.util

object EnvironmentDetection {
  def runningRemotely: Boolean =
    Option(System.getenv("AWS_EXECUTION_ENV")).exists(_.nonEmpty)

  def runningLocally: Boolean = !runningRemotely

  def location: Location =
    if (runningLocally) Location.Local else Location.Remote
}

object Environment {
  // TODO: Dont' hardcode QA
  def load: Environment = Environment(Realm.Qa, EnvironmentDetection.location)

  def realm: Realm = load.realm
}

case class Environment(
  realm: Realm,
  location: Location) {
  def withRealm(realm: Realm): Environment = this.copy(realm = realm)
}

// The data realm of the running application
sealed trait Realm
object Realm {
  case object Qa extends Realm
  case object Prod extends Realm
}

// The physical location of the running application
sealed trait Location
object Location {
  case object Local extends Location
  case object Remote extends Location
}
