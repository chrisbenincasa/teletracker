package com.teletracker.common.tasks.args.macros

import com.teletracker.common.tasks.args.{ArgParser, ReprArgParser}
import shapeless.labelled.KeyTag
import shapeless.{CNil, HList, HNil, Lazy}
import scala.annotation.tailrec
import scala.reflect.macros.blackbox

abstract class ArgParserMacros[RD[_]] {
  val c: blackbox.Context

  import c.universe._

  protected[this] def RD: TypeTag[RD[_]]

  protected[this] def hnilReprDecoder: Tree

  protected[this] def decodeMethodName: TermName
  protected[this] def decodeMethodArgs: List[Tree] = Nil

  protected[this] def decodeField(
    name: String,
    decode: TermName
  ): Tree

  private[this] def fullDecodeMethodArgs(tpe: Type): List[List[Tree]] =
    List(q"c: _root_.com.teletracker.common.tasks.args.ArgType") :: (if (decodeMethodArgs.isEmpty)
                                                                       Nil
                                                                     else
                                                                       List(
                                                                         decodeMethodArgs
                                                                       ))

  /**
    * Crash the attempted derivation because of a failure related to the
    * specified type.
    *
    * Note that these failures are not generally visible to users.
    */
  private[this] def fail(
    tpe: Type,
    str: String = ""
  ): Nothing =
    c.abort(
      c.enclosingPosition,
      s"Cannot generically derive instance: $tpe. ${str}"
    )

  /**
    * Represents an element at the head of a `shapeless.HList` or
    * `shapeless.Coproduct`.
    */
  private[this] case class Member(
    label: String,
    keyType: Type,
    valueType: Type,
    acc: Type,
    accTail: Type)

  /**
    * Represents an `shapeless.HList` or `shapeless.Coproduct` type in a way
    * that's more convenient to work with.
    */
  private[this] class Members(val underlying: List[Member]) {

    /**
      * Fold over the elements of this (co-)product while accumulating instances
      * of some type class for each.
      */
    def fold[Z](
      namePrefix: String
    )(
      resolver: Type => Tree
    )(
      init: Z
    )(
      f: (Member, TermName, Z) => Z
    ): (List[Tree], Z) = {
      val (instanceList, result) =
        underlying.foldRight((List.empty[(Type, (TermName, Tree))], init)) {
          case (
              member @ Member(label, _, valueType, _, _),
              (instanceList, acc)
              ) =>
            val (newInstanceList, instanceName) =
              instanceList.find(_._1 =:= valueType) match {
                case Some(result) => (instanceList, result._2._1)
                case None =>
                  val newName =
                    TermName(s"$namePrefix$label").encodedName.toTermName
                  val newInstance = resolver(valueType)

                  ((valueType, (newName, newInstance)) :: instanceList, newName)
              }

            (newInstanceList, f(member, instanceName, acc))
        }

      val instanceDefs = instanceList.map {
        case (_, (instanceName, instance)) =>
          q"private[this] val $instanceName = $instance"
      }

      (instanceDefs, result)
    }
  }

  private[this] val HListType: Type = typeOf[HList]

  private[this] object Members {
    private[this] val ShapelessSym = typeOf[HList].typeSymbol.owner
    private[this] val HNilSym = typeOf[HNil].typeSymbol
    private[this] val HConsSym = typeOf[shapeless.::[_, _]].typeSymbol
    private[this] val CNilSym = typeOf[CNil].typeSymbol
    private[this] val CConsSym = typeOf[shapeless.:+:[_, _]].typeSymbol
    private[this] val ShapelessLabelledType = typeOf[shapeless.labelled.type]
    private[this] val KeyTagSym = typeOf[KeyTag[_, _]].typeSymbol
    private[this] val ShapelessTagType = typeOf[shapeless.tag.type]
    private[this] val ScalaSymbolType = typeOf[scala.Symbol]

    case class Entry(
      label: String,
      keyType: Type,
      valueType: Type)

    object Entry {
      def unapply(tpe: Type): Option[(String, Type, Type)] = tpe.dealias match {

        /**
          * Before Scala 2.12 the `RefinedType` extractor returns the field type
          * (including any refinements) as the first result in the list.
          */
        case RefinedType(
            List(
              fieldType,
              TypeRef(lt, KeyTagSym, List(tagType, taggedFieldType))
            ),
            _
            )
            if lt =:= ShapelessLabelledType && fieldType =:= taggedFieldType =>
          tagType.dealias match {
            case RefinedType(
                List(
                  st,
                  TypeRef(
                    tt,
                    ts,
                    ConstantType(Constant(fieldKey: String)) :: Nil
                  )
                ),
                _
                ) if st =:= ScalaSymbolType && tt =:= ShapelessTagType =>
              Some((fieldKey, tagType, fieldType))
            case _ => None
          }

        /**
          * In Scala 2.12 the `RefinedType` extractor returns a refined type with
          * each individual refinement as a separate element in the list.
          */
        case RefinedType(parents, scope) =>
          parents.reverse match {
            case TypeRef(lt, KeyTagSym, List(tagType, taggedFieldType)) :: refs
                if lt =:= ShapelessLabelledType && internal
                  .refinedType(refs.reverse, scope) =:= taggedFieldType =>
              tagType.dealias match {
                case RefinedType(
                    List(
                      st,
                      TypeRef(
                        tt,
                        ts,
                        ConstantType(Constant(fieldKey: String)) :: Nil
                      )
                    ),
                    _
                    ) if st =:= ScalaSymbolType && tt =:= ShapelessTagType =>
                  Some((fieldKey, tagType, taggedFieldType))
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    }

    def fromType(tpe: Type): Members = tpe.dealias match {
      case TypeRef(ThisType(ShapelessSym), HNilSym | CNilSym, Nil) =>
        new Members(Nil)
      case acc @ TypeRef(
            ThisType(ShapelessSym),
            HConsSym | CConsSym,
            List(fieldType, tailType)
          ) =>
        fieldType match {
          case Entry(label, keyType, valueType) =>
            new Members(
              Member(label, keyType, valueType, acc, tailType) :: fromType(
                tailType
              ).underlying
            )
          case _ => fail(tpe, "fromType#Entry")
        }
      case _ => fail(tpe, "fromType")
    }
  }

  @tailrec
  private[this] def resolveInstance(
    tcs: List[(Type, Boolean)]
  )(
    tpe: Type
  ): Tree = tcs match {
    case (tc, lazily) :: rest =>
      val applied = c.universe.appliedType(tc.typeConstructor, List(tpe))
      val target =
        if (lazily)
          c.universe.appliedType(typeOf[Lazy[_]].typeConstructor, List(applied))
        else applied
      val inferred = c.inferImplicitValue(target, silent = true)

//      c.info(c.enclosingPosition, "Inferred: " + show(inferred), true)

      inferred match {
        case EmptyTree          => resolveInstance(rest)(tpe)
        case instance if lazily => q"{ $instance }.value"
        case instance           => instance
      }
    case Nil => fail(tpe, "resolveInstance")
  }

  val ReprDecoderUtils = symbolOf[ReprArgParser.type].asClass.module

  private[this] def hlistDecoderParts(
    members: Members
  ): (List[c.Tree], c.Tree) =
    members.fold("argParserFor")(
      resolveInstance(List((typeOf[ArgParser[_]], false)))
    )(q"$ReprDecoderUtils.hnilResult": Tree) {
      case (
          Member(label, nameTpe, tpe, _, accTail),
          instanceName,
          acc
          ) =>
        (
          q"""
        $ReprDecoderUtils.consResults[
          _root_.scala.util.Try,
          $nameTpe,
          $tpe,
          $accTail
        ](
          ${decodeField(label, instanceName)},
          $acc
        )(_root_.com.teletracker.common.tasks.args.ArgParser.tryInstance)
      """
        )
    }

  protected[this] def constructDecoder[R](
    implicit R: c.WeakTypeTag[R]
  ): c.Tree = {
    val isHList = R.tpe <:< HListType
//    val isCoproduct = !isHList && R.tpe <:< CoproductType

    if (!isHList) fail(R.tpe, "not an hlist")
    else {
      val members = Members.fromType(R.tpe)

      if (isHList && members.underlying.isEmpty) q"$hnilReprDecoder"
      else {
        val (instanceDefs, result) = hlistDecoderParts(members)
        val instanceType = appliedType(RD.tpe.typeConstructor, List(R.tpe))

        val tree = q"""
          new $instanceType {
            ..$instanceDefs

            final def $decodeMethodName(
              ...${fullDecodeMethodArgs(R.tpe)}
            ): _root_.scala.util.Try[$R] = $result
          }: $instanceType
        """

//        c.info(c.enclosingPosition, show(tree), true)

        tree
      }
    }
  }

//  protected[this] def constructCodec[R](
//    implicit R: c.WeakTypeTag[R]
//  ): c.Tree = {
//    val isHList = R.tpe <:< HListType
//    val isCoproduct = !isHList && R.tpe <:< CoproductType
//
//    if (!isHList && !isCoproduct) fail(R.tpe)
//    else {
//      val members = Members.fromType(R.tpe)
//
//      if (isHList && members.underlying.isEmpty) q"$hnilReprCodec"
//      else {
//        val (encoderInstanceDefs, (result, resultAccumulating)) =
//          if (isHList) hlistDecoderParts(members)
//          else coproductDecoderParts(members)
//
//        val (decoderInstanceDefs, encoderInstanceImpl) =
//          if (isHList) hlistEncoderParts(members)
//          else coproductEncoderParts(members)
//
//        val instanceType = appliedType(RC.tpe.typeConstructor, List(R.tpe))
//
//        q"""
//          new $instanceType {
//            ..$encoderInstanceDefs
//            ..$decoderInstanceDefs
//
//            final def $encodeMethodName(...${fullEncodeMethodArgs(R.tpe)}): _root_.io.circe.JsonObject =
//              $encoderInstanceImpl
//
//            final def $decodeMethodName(
//              ...${fullDecodeMethodArgs(R.tpe)}
//            ): _root_.io.circe.Decoder.Result[$R] = $result
//
//            final override def $decodeAccumulatingMethodName(
//              ...${fullDecodeAccumulatingMethodArgs(R.tpe)}
//            ): _root_.io.circe.Decoder.AccumulatingResult[$R] = $resultAccumulating
//          }: $instanceType
//        """
//      }
//    }
//  }
}
