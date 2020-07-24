package com.teletracker.common.tasks.args

import shapeless.Lazy

object semiauto {
  final def deriveDecoder[A](
    implicit decode: Lazy[DerivedArgParser[A]]
  ): ArgParser[A] = decode.value
}
