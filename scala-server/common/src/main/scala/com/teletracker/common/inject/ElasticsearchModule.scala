package com.teletracker.common.inject

import com.google.inject.Provides
import com.teletracker.common.config.TeletrackerConfig
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.elasticsearch.client.{RestClient, RestHighLevelClient}

class ElasticsearchModule extends TwitterModule {
  @Provides
  @Singleton
  def client(teletrackerConfig: TeletrackerConfig): RestHighLevelClient = {
    val credsProvider = new BasicCredentialsProvider()
    credsProvider.setCredentials(
      AuthScope.ANY,
      new UsernamePasswordCredentials(
        teletrackerConfig.elasticsearch.creds.user.get,
        teletrackerConfig.elasticsearch.creds.password.get
      )
    )

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
            httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
          }
        })
    )
  }
}
