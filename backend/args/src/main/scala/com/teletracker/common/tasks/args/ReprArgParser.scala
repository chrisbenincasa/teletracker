package com.teletracker.common.tasks.args

import cats.Apply
import shapeless.labelled.{field, FieldType}
import shapeless.{::, HList, HNil, Lazy, Witness}
import scala.util.{Failure, Success, Try}

abstract class ReprArgParser[A] extends ArgParser[A]

object ReprArgParser {
  implicit def deriveReprDecoder[R]: ReprArgParser[R] =
    macro Deriver.deriveDecoder[R]

  val hnilReprDecoder: ArgParser[HNil] = new ArgParser[HNil] {
    override def parse(in: ArgType): Try[HNil] =
      Failure(new IllegalStateException("HNil"))
  }

  def consResults[F[_], K, V, T <: HList](
    hv: F[V],
    tr: F[T]
  )(implicit F: Apply[F]
  ): F[FieldType[K, V] :: T] =
    F.map2(hv, tr)((v, t) => field[K].apply[V](v) :: t)

  val hnilResult: Try[HNil] = Success(HNil)
}

abstract class ReprArgParser2[A] extends ArgParser[A]

trait ReprArgParserLowPri {}

object ReprArgParser2 extends ReprArgParserLowPri {
  implicit def deriveHnil: ReprArgParser2[HNil] = new ReprArgParser2[HNil] {
    override def parse(in: ArgType): Try[HNil] = Success(HNil)
  }

  implicit def deriveReprDecoder[K <: Symbol, H, T <: HList](
    implicit witness: Witness.Aux[K],
    hParser: ArgParser[H],
    tParser: Lazy[ReprArgParser2[T]]
  ): ReprArgParser2[FieldType[K, H] :: T] = {
    val fieldName = witness.value.name
    new ReprArgParser2[FieldType[K, H] :: T] {
      override def parse(in: ArgType): Try[FieldType[K, H] :: T] = {
        in match {
          case AllArgs(args) =>
            for {
              h <- hParser.parse(ArgValue(args.get(fieldName)))
              t <- tParser.value.parse(args - fieldName)
            } yield {
              field[K](h) :: t
            }

          case ArgValue(_) => throw new IllegalArgumentException("Expected map")
        }
      }
    }
  }
}
