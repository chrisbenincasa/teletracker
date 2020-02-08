package com.teletracker.common.inject

import com.google.inject.Provides
import com.teletracker.common.config.TeletrackerConfig
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import org.apache.commons.io.IOUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.message.BasicHeader
import org.apache.http.protocol.HttpContext
import org.apache.http.{
  Header,
  HttpEntityEnclosingRequest,
  HttpHost,
  HttpRequest,
  HttpRequestInterceptor,
  NameValuePair
}
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.signer.Aws4Signer
import software.amazon.awssdk.auth.signer.params.{
  Aws4PresignerParams,
  Aws4SignerParams
}
import software.amazon.awssdk.http.{
  ContentStreamProvider,
  SdkHttpFullRequest,
  SdkHttpMethod
}
import java.io.InputStream
import java.util
import org.apache.http.protocol.HttpCoreContext.HTTP_TARGET_HOST
import software.amazon.awssdk.regions.Region

class ElasticsearchModule extends TwitterModule {
  @Provides
  @Singleton
  def client(
    teletrackerConfig: TeletrackerConfig,
    awsCredentialsProvider: AwsCredentialsProvider
  ): RestHighLevelClient = {
    val creds = for {
      user <- teletrackerConfig.elasticsearch.creds.user
      password <- teletrackerConfig.elasticsearch.creds.user
    } yield {
      val credsProvider = new BasicCredentialsProvider()
      credsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
          teletrackerConfig.elasticsearch.creds.user.get,
          teletrackerConfig.elasticsearch.creds.password.get
        )
      )
      credsProvider
    }

    val signer = Aws4Signer.create()

    new RestHighLevelClient(
      RestClient
        .builder(
          teletrackerConfig.elasticsearch.hosts.map(host => {
            new HttpHost(host.hostname, host.port, host.scheme)
          }): _*
        )
        .setHttpClientConfigCallback(new HttpClientConfigCallback {
          override def customizeHttpClient(
            httpClientBuilder: HttpAsyncClientBuilder
          ): HttpAsyncClientBuilder = {
            creds
              .map(httpClientBuilder.setDefaultCredentialsProvider)
              .getOrElse(httpClientBuilder)
              .addInterceptorLast(
                new AwsRequestSigningInterceptor(signer, awsCredentialsProvider)
              )
          }
        })
    )
  }

  private class AwsRequestSigningInterceptor(
    signer: Aws4Signer,
    credsProvider: AwsCredentialsProvider)
      extends HttpRequestInterceptor {

    override def process(
      request: HttpRequest,
      context: HttpContext
    ): Unit = {
      val uriBuilder = new URIBuilder(request.getRequestLine.getUri)

      val httpRequestBuilder =
        SdkHttpFullRequest
          .builder()
          .uri(uriBuilder.build())
          .protocol(request.getRequestLine.getProtocolVersion.getProtocol)
          .method(SdkHttpMethod.valueOf(request.getRequestLine.getMethod))

      val host = context.getAttribute(HTTP_TARGET_HOST).asInstanceOf[HttpHost]
      if (host != null) {
        httpRequestBuilder.host(host.getHostName)
      }

      request match {
        case httpEntityEnclosingRequest: HttpEntityEnclosingRequest =>
          if (httpEntityEnclosingRequest.getEntity != null)
            httpRequestBuilder.contentStreamProvider(
              () => httpEntityEnclosingRequest.getEntity.getContent
            )
        case _ =>
      }

      httpRequestBuilder.rawQueryParameters(nvpToMap(uriBuilder.getQueryParams))
      httpRequestBuilder.headers(headerArrayToMap(request.getAllHeaders))

      val sdkRequest = httpRequestBuilder.build()

      val signedRequest = signer.sign(
        sdkRequest,
        Aws4PresignerParams
          .builder()
          .awsCredentials(credsProvider.resolveCredentials())
          .signingName("es")
          .signingRegion(Region.US_WEST_2)
          .build()
      )

      request.setHeaders(mapToHeaderArray(signedRequest.headers()))

      request match {
        case httpEntityEnclosingRequest: HttpEntityEnclosingRequest =>
          if (httpEntityEnclosingRequest.getEntity != null) {
            sdkRequest
              .contentStreamProvider()
              .ifPresent(csp => {
                val basicHttpEntity = new BasicHttpEntity
                basicHttpEntity.setContent(csp.newStream())
                httpEntityEnclosingRequest.setEntity(basicHttpEntity)
              })
          }
        case _ =>
      }
    }

    private def nvpToMap(
      nvps: java.util.List[NameValuePair]
    ): util.HashMap[String, util.List[String]] = {
      val headers = new util.HashMap[String, java.util.List[String]]()
      nvps.forEach(nvp => {
        val list = headers
          .computeIfAbsent(nvp.getName, _ => new util.ArrayList[String]())
        list.add(nvp.getValue)
      })
      headers
    }

    private def headerArrayToMap(
      headers: Array[Header]
    ): util.HashMap[String, util.List[String]] = {
      val headersMap = new util.HashMap[String, util.List[String]]()
      for (header <- headers) {
        if (!skipHeader(header)) {
          val value = new util.ArrayList[String](1)
          value.add(header.getValue)
          headersMap.put(header.getName, value)
        }
      }
      headersMap
    }

    private def skipHeader(header: Header) =
      ("content-length".equalsIgnoreCase(header.getName) && "0" == header.getValue) || "host"
        .equalsIgnoreCase(header.getName)
  }

  private def mapToHeaderArray(
    mapHeaders: util.Map[String, util.List[String]]
  ) = {
    val headers = new Array[Header](mapHeaders.size())
    var i = 0
    mapHeaders.entrySet.forEach(entry => {
      headers.update(i, new BasicHeader(entry.getKey, entry.getValue.get(0)))
      i += 1
    })
    headers
  }
}
