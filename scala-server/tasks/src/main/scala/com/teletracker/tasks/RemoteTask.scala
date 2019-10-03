package com.teletracker.tasks

import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessageFactory
import io.circe.syntax._
import javax.inject.Inject

class RemoteTask @Inject()(
  publisher: Publisher,
  teletrackerTaskRunner: TeletrackerTaskRunner)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val clazz = args.value[String]("classToRun").get
    val instance =
      teletrackerTaskRunner.getInstance(clazz)

    val jsonArgs = instance.argsAsJson(args)

    val message =
      TeletrackerTaskQueueMessageFactory.withJsonArgs(clazz, jsonArgs, None)
    println(message.asJson)

    publisher
      .publish(
        PubsubMessage
          .newBuilder()
          .setData(ByteString.copyFrom(message.asJson.noSpaces.getBytes()))
          .build()
      )
      .get()
  }
}
