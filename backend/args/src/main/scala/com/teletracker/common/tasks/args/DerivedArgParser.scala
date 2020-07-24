package com.teletracker.common.tasks.args

import io.circe.generic.extras.util.RecordToMap
import shapeless.ops.hlist.{Unzip, Zip}
import shapeless.{::, Annotations, Default, HList, LabelledGeneric, Lazy}
import scala.util.{Failure, Success, Try}

abstract class DerivedArgParser[A] extends ArgParser[A]

object DerivedArgParser {
  implicit def deriveDecoder[
    A,
    R <: HList,
    Defaults <: HList
//    Annos <: HList,
//    Zipped <: HList
  ](implicit
    gen: LabelledGeneric.Aux[A, R],
    defaults: Default.AsRecord.Aux[A, Defaults],
    defaultMapper: RecordToMap[Defaults],
//    annos: Annotations.Aux[ArgParserField, A, Annos],
//    zipped: Zip.Aux[R :: Annos, Zipped],
//    unzipped: Unzip.Aux[Zipped, (R, Annos)],
    decode: Lazy[ReprArgParser2[R]]
  ): DerivedArgParser[A] = new DerivedArgParser[A] {
    val defaultsAsMap: Map[String, Any] = defaultMapper(defaults())
    override def parse(in: ArgType): Try[A] = {
      in match {
        case AllArgs(args) =>
          decode.value.parse(defaultsAsMap ++ args) match {
            case f @ Failure(_) => f.asInstanceOf[Try[A]]
            case Success(value) => Success(gen.from(value))
          }
        case ArgValue(_) =>
          Failure(
            new IllegalArgumentException("Got value when map was expected")
          )
      }
    }
  }
}

case class ArgParserField(field: String)
