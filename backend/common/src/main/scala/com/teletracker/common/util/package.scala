package com.teletracker.common

import cats.arrow.FunctionK
import com.twitter.util.{
  Return,
  Throw,
  Future => TFuture,
  Promise => TPromise,
  Try => TTry
}
import scala.util.{Failure, Success, Try => STry}
import scala.concurrent.{
  ExecutionContext,
  Future => SFuture,
  Promise => SPromise
}
import cats.~>

package object util {
  final val UuidRegex =
    "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}".r

  object Implicits {
    implicit def scalaTwitterTryFunctionK: STry ~> TTry =
      new FunctionK[STry, TTry] {
        override def apply[A](fa: STry[A]): TTry[A] = {
          fa match {
            case Success(x) => Return(x)
            case Failure(x) => Throw(x)
          }
        }
      }

    implicit def twitterScalaTryFunctionK: TTry ~> STry =
      new FunctionK[TTry, STry] {
        override def apply[A](fa: TTry[A]): STry[A] = {
          fa match {
            case Return(x) => Success(x)
            case Throw(x)  => Failure(x)
          }
        }
      }

    implicit def scalaTryToTwitterTry[T](s: STry[T]): TTry[T] = {
      s match {
        case Success(x) => Return(x)
        case Failure(x) => Throw(x)
      }
    }

    implicit def twitterTryToScalaTry[A](fa: TTry[A]): STry[A] = {
      fa match {
        case Return(x) => Success(x)
        case Throw(x)  => Failure(x)
      }
    }

    implicit def twitterFutureToScalaFuture[T](t: TFuture[T]): SFuture[T] = {
      val p = SPromise[T]()
      t.respond(p.complete(_))
      p.future
    }

    implicit def scalaFutureToTwitterFuture[T](
      s: SFuture[T]
    )(implicit executionContext: ExecutionContext
    ): TFuture[T] = {
      val p = TPromise[T]()
      s.onComplete(p.update(_))
      p
    }
  }
}

class ScalaToTwitterFuture[T](val f: SFuture[T]) extends AnyVal {
  import util.Implicits._
  def toTwitterFuture(
    implicit executionContext: ExecutionContext
  ): TFuture[T] = {
    val p = TPromise[T]()
    f.onComplete(p.update(_))
    p
  }
}

class TwitterToScalaFuture[T](val f: TFuture[T]) extends AnyVal {
  import util.Implicits._

  def toScalaFuture(): SFuture[T] = {
    val p = SPromise[T]()
    f.respond(p.complete(_))
    p.future
  }
}
