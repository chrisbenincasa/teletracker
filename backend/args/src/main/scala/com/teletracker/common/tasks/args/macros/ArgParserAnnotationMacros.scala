package com.teletracker.common.tasks.args.macros

import com.teletracker.common.tasks.args.ArgParser
import scala.reflect.macros.blackbox

abstract class ArgParserAnnotationMacros {
  val c: blackbox.Context

  import c.universe._

  protected[this] def semiautoObj: Symbol
  protected[this] def deriveMethodPrefix: String

  private[this] def isCaseClassOrSealed(clsDef: ClassDef) =
    clsDef.mods.hasFlag(Flag.CASE) || clsDef.mods.hasFlag(Flag.SEALED)

  final protected[this] def constructJsonCodec(annottees: Tree*): Tree =
    annottees match {
      case List(clsDef: ClassDef) if isCaseClassOrSealed(clsDef) =>
        q"""
       $clsDef
       object ${clsDef.name.toTermName} {
         ..${codec(clsDef)}
       }
       """
      case List(
          clsDef: ClassDef,
          q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) if isCaseClassOrSealed(clsDef) =>
        q"""
       $clsDef
       $mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
         ..$objDefs
         ..${codec(clsDef)}
       }
       """
      case _ =>
        c.abort(
          c.enclosingPosition,
          "Invalid annotation target: must be a case class or a sealed trait/class"
        )
    }

  private[this] val DecoderClass = typeOf[ArgParser[_]].typeSymbol.asType

  private[this] val macroName: Tree = {
    c.prefix.tree match {
      case Apply(Select(New(name), _), _) => name
      case _                              => c.abort(c.enclosingPosition, "Unexpected macro application")
    }
  }

  private[this] def codec(clsDef: ClassDef): List[Tree] = {
    val tpname = clsDef.name
    val tparams = clsDef.tparams
    val decodeName = TermName("decode" + tpname.decodedName)
    def deriveName(suffix: String) = TermName(deriveMethodPrefix + suffix)
    if (tparams.isEmpty) {
      val Type = tpname
      List(
        q"""implicit val $decodeName: $DecoderClass[$Type] = $semiautoObj.${deriveName(
          "Decoder"
        )}[$Type]"""
      )

    } else {
      val tparamNames = tparams.map(_.name)
      def mkImplicitParams(
        prefix: String,
        typeSymbol: TypeSymbol
      ) =
        tparamNames.zipWithIndex.map {
          case (tparamName, i) =>
            val paramName = TermName(s"$prefix$i")
            val paramType = tq"$typeSymbol[$tparamName]"
            q"$paramName: $paramType"
        }
      val decodeParams = mkImplicitParams("decode", DecoderClass)
      val Type = tq"$tpname[..$tparamNames]"

      List(
        q"""implicit def $decodeName[..$tparams](implicit ..$decodeParams): $DecoderClass[$Type] =
            $semiautoObj.${deriveName("Decoder")}[$Type]"""
      )
    }
  }
}
