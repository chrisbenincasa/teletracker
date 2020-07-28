package com.teletracker.common.tasks.args

import com.teletracker.common.tasks.args.macros.ArgParserMacros
import scala.reflect.macros.whitebox

class Deriver(val c: whitebox.Context)
    extends ArgParserMacros[
      ReprArgParser
    ] {
  import c.universe._

  def deriveDecoder[R: c.WeakTypeTag]: c.Expr[ReprArgParser[R]] =
    c.Expr[ReprArgParser[R]](constructDecoder[R])

  protected[this] val RD: TypeTag[ReprArgParser[_]] = c.typeTag

  protected[this] val hnilReprDecoder: Tree =
    q"_root_.com.teletracker.common.tasks.args.ReprArgParser.hnilReprDecoder"

  protected[this] val decodeMethodName: TermName = TermName("parse")

  protected[this] def decodeField(
    name: String,
    decode: TermName
  ): Tree = {
    q"""
       c match {
          case _root_.com.teletracker.common.tasks.args.AllArgs(a) =>
            $decode.parse(_root_.com.teletracker.common.tasks.args.ArgValue(a.get($name))) match {
              case v @ _root_.scala.util.Success(_) => v
              case _root_.scala.util.Failure(e) =>
                _root_.scala.util.Failure(new IllegalArgumentException("Could not parse field " + $name, e))
            }
          case _root_.com.teletracker.common.tasks.args.ArgValue(v) =>
            _root_.scala.util.Failure(new IllegalArgumentException("Expected all args, got value."))
       }
       """
  }
}
