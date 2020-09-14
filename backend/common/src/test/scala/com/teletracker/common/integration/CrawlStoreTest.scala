package com.teletracker.common.integration

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{HostConfig, PortBinding}
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import com.teletracker.common.config.core.StaticLoader
import com.teletracker.common.config.{DynamoTableConfig, TeletrackerConfig}
import com.teletracker.common.db.dynamo.{
  CrawlStore,
  CrawlerName,
  HistoricalCrawl
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Retry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Outcome}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import java.net.{InetAddress, URI}
import java.time.Instant
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

class CrawlStoreTest
    extends AnyFlatSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  private val dockerConfig =
    DefaultDockerClientConfig.createDefaultConfigBuilder().build()

  private var dynamoContainer: DynamoContainer = _
  private var dockerClient: DockerClient = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val dockerHttpClient = new ZerodepDockerHttpClient.Builder()
      .dockerHost(dockerConfig.getDockerHost)
      .sslConfig(dockerConfig.getSSLConfig)
      .build()

    dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient)

    dockerClient
      .pullImageCmd("amazon/dynamodb-local:1.13.2")
      .start()
      .awaitCompletion()

    val port = 32768 + Random.nextInt(60999 - 32768)

    val container =
      dockerClient
        .createContainerCmd("amazon/dynamodb-local:1.13.2")
        .withHostConfig(
          HostConfig
            .newHostConfig()
            .withPortBindings(PortBinding.parse(s"$port:8000"))
        )
        .exec()

    val containerId = container.getId
    dockerClient.startContainerCmd(containerId).exec()

    val localhost = InetAddress.getLoopbackAddress.getHostAddress

    val client = new Retry(Executors.newSingleThreadScheduledExecutor())
      .withRetries(Retry.RetryOptions(3, 5 seconds, 1 minute))(() => {
        Future {
          DynamoDbAsyncClient
            .builder()
            .endpointOverride(URI.create(s"http://$localhost:$port"))
            .build()
        }
      })
      .await()

    dynamoContainer = DynamoContainer(
      containerId,
      port,
      client
    )
  }

  override protected def afterAll(): Unit = {
    super.afterAll()

    if (dynamoContainer ne null) {
      dockerClient.stopContainerCmd(dynamoContainer.containerId).exec()
      dynamoContainer.client.close()
    }

    if (dockerClient ne null) {
      dockerClient.close()
    }
  }

  override protected def beforeEach(): Unit = {
    import scala.compat.java8.FutureConverters._
    super.beforeEach()

    dynamoContainer.client
      .createTable(
        CreateTableRequest
          .builder()
          .tableName("crawls")
          .keySchema(
            KeySchemaElement
              .builder()
              .attributeName("spider")
              .keyType(KeyType.HASH)
              .build(),
            KeySchemaElement
              .builder()
              .attributeName("version")
              .keyType(KeyType.RANGE)
              .build()
          )
          .attributeDefinitions(
            AttributeDefinition
              .builder()
              .attributeName("spider")
              .attributeType(ScalarAttributeType.S)
              .build(),
            AttributeDefinition
              .builder()
              .attributeName("version")
              .attributeType(ScalarAttributeType.N)
              .build()
          )
          .billingMode(BillingMode.PAY_PER_REQUEST)
          .build()
      )
      .toScala
      .await()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
  }

  trait ConfiguredTest {
    import com.teletracker.common.config.core.readers.ValueReaders._
    lazy val staticConfig = new StaticLoader()
      .static[TeletrackerConfig](TeletrackerConfig.path)
      .map(conf => {
        val ddb = conf.dynamo
        conf.copy(
          dynamo = ddb.copy(crawls = DynamoTableConfig("crawls"))
        )
      })
  }

  it should "wait until crawl is complete" in new ConfiguredTest {
    val crawlStore = new CrawlStore(
      staticConfig,
      dynamoContainer.client,
      Executors.newSingleThreadScheduledExecutor()
    )

    val spiderName = new CrawlerName("amazon")
    val version = System.currentTimeMillis()
    val now = Instant.now()

    val crawl: HistoricalCrawl = HistoricalCrawl(
      spiderName.name,
      version,
      Some(now),
      timeClosed = None,
      totalItemsScraped = None,
      metadata = None,
      numOpenSpiders = Some(1),
      isDistributed = Some(true)
    )

    crawlStore
      .saveCrawl(
        crawl
      )
      .await()

    val foundCrawl = crawlStore.getCrawlAtVersion(spiderName, version).await()

    assert(foundCrawl.isDefined)

    val token =
      crawlStore
        .waitForActiveCrawlCompletion(
          spiderName,
          Some(version),
          frequency = 1 second
        )
        .await()

    Future {
      crawlStore
        .saveCrawl(
          crawl.copy(
            timeClosed = Some(Instant.now()),
            numOpenSpiders = Some(0)
          )
        )
        .andThen {
          case Failure(exception) => fail(exception)
          case Success(_) =>
            println("Successfully saved crawl")
        }
    }

    token.await(15 seconds)
  }

  case class DynamoContainer(
    containerId: String,
    port: Int,
    client: DynamoDbAsyncClient)
}
