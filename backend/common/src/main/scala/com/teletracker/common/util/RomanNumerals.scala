package com.teletracker.common.util

object RomanNumerals {
  case class RomanUnit(
    value: Int,
    token: String)

  final val RomanNumerals = List(
    RomanUnit(1000, "M"),
    RomanUnit(900, "CM"),
    RomanUnit(500, "D"),
    RomanUnit(400, "CD"),
    RomanUnit(100, "C"),
    RomanUnit(90, "XC"),
    RomanUnit(50, "L"),
    RomanUnit(40, "XL"),
    RomanUnit(10, "X"),
    RomanUnit(9, "IX"),
    RomanUnit(5, "V"),
    RomanUnit(4, "IV"),
    RomanUnit(1, "I")
  )

  def toRoman(num: Int): String = {
    var remainingNumber = num
    RomanNumerals.foldLeft("") { (outputStr, romanUnit) =>
      val times = remainingNumber / romanUnit.value
      remainingNumber -= romanUnit.value * times
      outputStr + (romanUnit.token * times)
    }
  }

  def fromRoma2(s: String): Int = {
    val numerals = Map(
      'I' -> 1,
      'V' -> 5,
      'X' -> 10,
      'L' -> 50,
      'C' -> 100,
      'D' -> 500,
      'M' -> 1000
    )

    s.toUpperCase
      .map(numerals)
      .foldLeft((0, 0)) {
        case ((sum, last), curr) =>
          (sum + curr + (if (last < curr) -2 * last else 0), curr)
      }
      ._1
  }
}
