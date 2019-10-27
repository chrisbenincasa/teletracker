package com.teletracker.service.controllers.validation

import com.twitter.finatra.validation.{
  ErrorCode,
  Range,
  ValidationMessageResolver,
  ValidationResult,
  Validator
}
import java.time.LocalDate

object ItemReleaseYearValidator {
  def errorMessage(
    resolver: ValidationMessageResolver,
    value: Any,
    minValue: Long,
    maxValue: Long
  ): String = {

    resolver.resolve(classOf[Range], value, minValue, maxValue)
  }
}

class ItemReleaseYearValidator(
  validationMessageResolver: ValidationMessageResolver,
  annotation: ItemReleaseYear)
    extends Validator[ItemReleaseYear, Any](
      validationMessageResolver,
      annotation
    ) {

  private val min = 1900
  private val max = LocalDate.now().getYear + 5

  override def isValid(value: Any): ValidationResult = {
    value match {
      case numberValue: Number =>
        validationResult(numberValue)
      case _ =>
        throw new IllegalArgumentException(
          s"Class [${value.getClass}] is not supported by ${this.getClass}"
        )
    }
  }

  private def validationResult(value: Number) = {
    val doubleValue = value.doubleValue()

    ValidationResult.validate(
      min <= doubleValue && doubleValue <= max,
      errorMessage(value),
      errorCode(value)
    )
  }

  private def errorMessage(value: Number) = {
    ItemReleaseYearValidator.errorMessage(
      validationMessageResolver,
      value,
      min,
      max
    )
  }

  private def errorCode(value: Number) = {
    ErrorCode.ValueOutOfRange(
      java.lang.Long.valueOf(value.longValue),
      min,
      max
    )
  }
}
