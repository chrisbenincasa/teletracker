package com.teletracker.common.elasticsearch

import com.google.common.collect.HashBiMap
import scala.collection.JavaConverters._

// TODO: See if we can use an Elasticsearch dictionary to power these...
object FullTextSynonyms {
  final val Synonyms = HashBiMap.create[String, String](
    Map(
      "first" -> "1st",
      "second" -> "2nd",
      "third" -> "3rd",
      "fourth" -> "4th",
      "fifth" -> "5th",
      "sixth" -> "6th",
      "seventh" -> "7th",
      "eighth" -> "8th",
      "ninth" -> "9th",
      "tenth" -> "10th",
      "and" -> "&"
    ).asJava
  )

  def containsSynonym(query: String): Boolean = {
    Synonyms.containsKey(query.toLowerCase()) || Synonyms
      .inverse()
      .containsKey(query.toLowerCase)
  }

  def getSynonym(word: String): Option[String] = {
    Option(Synonyms.get(word)).orElse(Option(Synonyms.inverse().get(word)))
  }

  def replaceWithSynonym(query: String): Option[String] = {
    val result = query
      .split(" ")
      .flatMap(word => {
        val lowercased = word.toLowerCase
        if (containsSynonym(lowercased)) {
          getSynonym(lowercased)
        } else {
          Some(word)
        }
      })
      .mkString(" ")

    if (result == query) {
      None
    } else {
      Some(result)
    }
  }
}
