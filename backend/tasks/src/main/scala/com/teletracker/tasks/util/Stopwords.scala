package com.teletracker.tasks.util

import javax.inject.{Inject, Singleton}
import scala.collection.mutable
import scala.io.Source

@Singleton
class Stopwords @Inject()() {
  private[this] var didInit: Boolean = false
  private[this] val words: mutable.Set[String] = mutable.Set()

  def get(): Set[String] = {
    init()

    words.toSet
  }

  def removeStopwords(target: String): String = {
    val words = get()
    target
      .split(" ")
      .filterNot(word => words.contains(word.toLowerCase()))
      .mkString(" ")
  }

  def init(): Unit = synchronized {
    if (!didInit) {
      val source = Source.fromInputStream(
        getClass.getClassLoader.getResourceAsStream("english_stopwords.txt")
      )
      try {
        words ++= source.getLines()
        didInit = true
      } finally {
        source.close()
      }
    }
  }

}
