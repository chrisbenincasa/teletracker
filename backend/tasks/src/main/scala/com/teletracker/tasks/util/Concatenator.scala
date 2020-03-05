package com.teletracker.tasks.util

import javax.inject.Inject
import java.io.{BufferedOutputStream, FileOutputStream, PrintStream}
import java.net.URI
import java.nio.file.Files

class Concatenator @Inject()(
  sourceRetriever: SourceRetriever,
  sourceWriter: SourceWriter) {
  def concatenate(
    source: URI,
    destination: URI
  ): Unit = {
    val tmp = Files.createTempFile("concat", "tmp")
    tmp.toFile.deleteOnExit()

    val output = new PrintStream(
      new BufferedOutputStream(new FileOutputStream(tmp.toFile))
    )

    var lines = 0
    sourceRetriever
      .getSourceStream(source)
      .foreach(source => {
        source
          .getLines()
          .foreach(line => {
            lines += 1
            output.println(line)
          })
      })

    output.flush()

    println(s"Handled a total of ${lines} lines")

    sourceWriter.writeFile(destination, tmp)
  }
}
