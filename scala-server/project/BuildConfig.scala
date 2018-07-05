object BuildConfig {
  object Revision {
    lazy val revision = System.getProperty("revision", "SNAPSHOT")
  }
}
