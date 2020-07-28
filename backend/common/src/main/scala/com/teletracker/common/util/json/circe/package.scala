package com.teletracker.common.util.json

import com.teletracker.common.tasks.args.ArgParser.Millis
import io.circe.Codec
import shapeless.tag
import shapeless.tag.@@
import scala.concurrent.duration._

package object circe
    extends ModelInstances
    with JodaInstances
    with TmdbInstances {

  implicit final val finiteDurationMillisCodec
    : Codec[FiniteDuration @@ Millis] =
    Codec.from(
      io.circe.Decoder.decodeLong.map(l => tag[Millis](l millis)),
      io.circe.Encoder.encodeLong.contramap(_.toMillis)
    )
}
