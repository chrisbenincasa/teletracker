import sbt.io._
import java.io.File
import java.time.OffsetDateTime

object VersionFileGenerate {
  def generate(
    resourceDir: File,
    version: String
  ): Seq[File] = {
    val now = OffsetDateTime.now()

    val values = List(
      "BUILT_AT" -> now.toString,
      "VERSION" -> version
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
