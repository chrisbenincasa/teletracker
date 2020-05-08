import sbt.io._
import java.io.File
import java.time.OffsetDateTime
import scala.sys.process._

object VersionFileGenerate {
  def generate(
    resourceDir: File,
    version: String
  ): Seq[File] = {
    val now = OffsetDateTime.now()

    val values = List(
      "BUILT_AT" -> now.toString,
      "VERSION" -> version,
      "GIT_SHA" -> "git rev-parse --short HEAD".!!.trim
    ).map {
        case (x, y) => s"$x=$y"
      }
      .mkString("\n")

    val versionFile = new File(
      s"${resourceDir.getAbsolutePath}/version_info.txt"
    )
    IO.write(versionFile, values)

    Seq(versionFile)
  }
}
