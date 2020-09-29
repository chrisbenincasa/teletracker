package com.teletracker.common.util

import _root_.shapeless.ops.product._
import _root_.shapeless.syntax.std.product._

package object shapeless extends CaseClassImplicits {}

trait CaseClassImplicits {
  implicit def toProductMapOps[A <: Product](a: A): ToMapOps[A] =
    new ToMapOps[A](a)
}

final class ToMapOps[A <: Product](val a: A) extends AnyVal {
  def mkMapAny(implicit toMap: ToMap.Aux[A, Symbol, Any]): Map[String, Any] =
    a.toMap[Symbol, Any]
      .map { case (k: Symbol, v) => k.name -> v }

  def mkMapAnyUnwrapOptions(
    implicit toMap: ToMap.Aux[A, Symbol, Any]
  ): Map[String, Any] =
    a.toMap[Symbol, Any]
      .collect {
        case (k: Symbol, v: Option[_]) if v.isDefined => k.name -> v.get
        case (k: Symbol, v)                           => k.name -> v
      }
}
