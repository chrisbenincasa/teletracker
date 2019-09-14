package com.teletracker.common.util

import com.teletracker.common.db.BaseDbProvider

trait FactoryImplicits {
  implicit def autoFactoryToInstance[T](
    fac: GeneralizedDbFactory[T]
  )(implicit baseDbProvider: BaseDbProvider
  ): T = {
    fac.create(baseDbProvider)
  }
}

object FactoryImplicits extends FactoryImplicits

trait GeneralizedDbFactory[T] {
  def create(baseDbProvider: BaseDbProvider): T
}
