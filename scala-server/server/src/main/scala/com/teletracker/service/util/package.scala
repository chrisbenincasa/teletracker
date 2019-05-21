package com.teletracker.service

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
