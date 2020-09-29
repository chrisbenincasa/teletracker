package com.teletracker.common.testing.docker

import org.apache.http.HttpHost
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import java.net.InetAddress
import scala.concurrent.duration._

object ElasticsearchContainer {
  def create(): ElasticsearchContainer = {
    val manager = new ContainerManager()
    val container = manager.create(
      ContainerConfig(
        "elasticsearch:7.4.2",
        port = Some(9200),
        envVars = Seq("discovery.type=single-node"),
        waitForLogLine = Some("Active license is now"),
        maxWaitForLogLine = Some(1 minute)
      )
    )

    ElasticsearchContainer(container.portMap(9200), container)
  }
}

case class ElasticsearchContainer(
  port: Int,
  container: Container)
    extends AutoCloseable {
  lazy val client =
    new RestHighLevelClient(
      RestClient
        .builder(
          new HttpHost(InetAddress.getLoopbackAddress, port)
        )
    )

  override def close(): Unit = container.close()
}
