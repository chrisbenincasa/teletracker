package com.teletracker.common.util

object Ratings {
  // https://stackoverflow.com/a/1411268
  // (R * v + C * m) / (v + m)
  def weightedAverage(
    averageRating: Double,
    numRatings: Int,
    overallAverage: Double,
    weight: Int
  ): Double = {
    val calc = (averageRating * numRatings + overallAverage * weight) / (numRatings + weight)
    roundTwoHundredths(calc)
  }

  def roundTwoHundredths(rating: Double): Double =
    Math.round(rating * 100.0) / 100.0
}
