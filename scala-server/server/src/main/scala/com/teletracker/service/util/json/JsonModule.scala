package com.teletracker.service.util.json

import com.fasterxml.jackson.databind.{
  Module,
  PropertyNamingStrategy,
  SerializationFeature
}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finatra.json.modules.FinatraJacksonModule

class JsonModule extends FinatraJacksonModule {
  // add in our tiny type serializer
  override val additionalJacksonModules: Seq[Module] = baseModules

  override protected val propertyNamingStrategy =
    PropertyNamingStrategy.LOWER_CAMEL_CASE

  override protected val serializationConfig = {
    Map(
//      SerializationFeature.WRITE_DATES_AS_TIMESTAMPS -> true,
      SerializationFeature.WRITE_ENUMS_USING_TO_STRING -> true
    )
  }

  def baseModules: Seq[Module] = {
    Seq(new DefaultScalaModule, new JodaModule)
  }
}
