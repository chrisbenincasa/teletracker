package com.teletracker.common.tasks.args

import com.teletracker.common.tasks.args.macros.ArgParserAnnotationMacros
import scala.reflect.macros.blackbox

class GenArgParser extends scala.annotation.StaticAnnotation {
  def macroTransform(annottees: Any*): Any =
    macro GenericArgParserMacros.argParserAnnotationMacro
}

final private[tasks] class GenericArgParserMacros(val c: blackbox.Context)
    extends ArgParserAnnotationMacros {
  import c.universe._

  protected[this] def semiautoObj: Symbol =
    symbolOf[semiauto.type].asClass.module

  protected[this] def deriveMethodPrefix: String = "derive"

  def argParserAnnotationMacro(annottees: Tree*): Tree =
    constructJsonCodec(annottees: _*)
}
