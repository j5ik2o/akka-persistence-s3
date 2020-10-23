package com.github.j5ik2o.akka.persistence.s3.journal

import com.github.j5ik2o.akka.persistence.s3.config.JournalPluginConfig
import com.github.j5ik2o.akka.persistence.s3.config.client.{ ClientType, ClientVersion }
import com.github.j5ik2o.akka.persistence.s3.resolver.BucketNameResolver
import com.typesafe.config.{ Config, ConfigFactory }

object ConfigHelper {

  def config(
      defaultResource: String,
      s3Port: Int,
      accessKeyId: String,
      secretAccessKey: String,
      clientVersion: ClientVersion.Value = ClientVersion.V2,
      clientType: ClientType.Value = ClientType.Async
  ): Config = {
    val configString = s"""
       |akka.persistence.journal.plugin = "j5ik2o.s3-journal"
       |akka.persistence.snapshot-store.plugin = "j5ik2o.s3-snapshot-store"
       |j5ik2o.s3-journal {
       |  class = "${classOf[S3Journal].getName}"
       |  bucket-name = "${JournalPluginConfig.defaultBucketName}"
       |  s3-client {
       |    access-key-id = "${accessKeyId}"
       |    secret-access-key = "${secretAccessKey}"
       |    endpoint = "http://127.0.0.1:${s3Port}"
       |    s3-options {
       |      path-style-access-enabled = true
       |    }
       |  }
       |}
       |
       |j5ik2o.s3-snapshot-store {
       |  s3-client {
       |    access-key-id = "${accessKeyId}"
       |    secret-access-key = "${secretAccessKey}"
       |    endpoint = "http://127.0.0.1:${s3Port}"
       |    s3-options {
       |      path-style-access-enabled = true
       |    }
       |  }
       |}
       """.stripMargin
    val config = ConfigFactory
      .parseString(
        configString
      )
      .withFallback(ConfigFactory.load(defaultResource))
    // println(ConfigRenderUtils.renderConfigToString(config))
    config
  }
}
