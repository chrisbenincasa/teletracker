package com.teletracker.service.util.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

class ClassNameJsonSerializer(t: Class[Any]) extends StdSerializer[Any](t) {
  def this() = this(null)

  override def serialize(value: Any, gen: JsonGenerator, provider: SerializerProvider): Unit = {
    gen.writeString(value.getClass.getName)
  }
}
