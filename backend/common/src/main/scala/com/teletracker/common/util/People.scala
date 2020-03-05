package com.teletracker.common.util

import com.teletracker.common.model.tmdb.{Movie, Person}
import java.time.LocalDate

object People {
  implicit def toRichPerson(p: Person): RichPerson = new RichPerson(p)
}

class RichPerson(val person: Person) extends AnyVal {
  def releaseYear: Option[Int] =
    person.birthday
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_).getYear)
}
