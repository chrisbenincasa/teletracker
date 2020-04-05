package com.teletracker.service.controllers.validation

import com.teletracker.service.controllers.annotations.RatingRange
import com.twitter.finatra.validation.{
  ErrorCode,
  Range,
  ValidationMessageResolver,
  ValidationResult,
  Validator
}

object RatingRangeValidator {
  def errorMessage(
    resolver: ValidationMessageResolver,
    valueMin: Any,
    valueMax: Any,
    minValue: Double,
    maxValue: Double
  ): String = {
    resolver.resolve(classOf[Range], valueMin, valueMax, minValue, maxValue)
  }
}

class RatingRangeValidator(
  validationMessageResolver: ValidationMessageResolver,
  annotation: RatingRange)
    extends Validator[RatingRange, Any](
      validationMessageResolver,
      annotation
    ) {

  private val min = annotation.min()
  private val max = annotation.max()

  override def isValid(value: Any): ValidationResult = {
    value match {
      case stringValue: String =>
        val Array(givenMin, givenMax) = stringValue.split(":", 2)
        if (givenMin.isEmpty && givenMax.isEmpty) {
          ValidationResult.Invalid(
            "Invalid string",
            ErrorCode.RequiredFieldMissing
          )
        } else {
          validationResult(
            Option(givenMin).filter(_.nonEmpty).map(_.toDouble),
            Option(givenMax).filter(_.nonEmpty).map(_.toDouble)
          )
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Class [${value.getClass}] is not supported by ${this.getClass}"
        )
    }
  }

  private def validationResult(
    givenMin: Option[Number],
    givenMax: Option[Number]
  ) = {
    ValidationResult.validate(
      givenMin
        .map(_.doubleValue())
        .forall(min <= _) && givenMax.map(_.doubleValue()).forall(_ <= max),
      errorMessage(givenMin, givenMax),
      errorCode(givenMin, givenMax)
    )
  }

  private def errorMessage(
    givenMin: Option[Number],
    givenMax: Option[Number]
  ) = {
    RatingRangeValidator.errorMessage(
      validationMessageResolver,
      givenMin,
      givenMax,
      min,
      max
    )
  }

  private def errorCode(
    givenMin: Option[Number],
    givenMax: Option[Number]
  ) = {
    MinMaxOutOfRange(
      givenMin,
      givenMax,
      min.toLong,
      max.toLong
    )
  }
}

case class MinMaxOutOfRange(
  givenMin: Option[Number],
  givenMax: Option[Number],
  min: Double,
  max: Double)
    extends ErrorCode
