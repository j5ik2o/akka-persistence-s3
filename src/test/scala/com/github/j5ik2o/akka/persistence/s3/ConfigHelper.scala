package com.github.j5ik2o.akka.persistence.s3

import com.github.j5ik2o.akka.persistence.s3.config.{ JournalPluginConfig, SnapshotPluginConfig }
import com.github.j5ik2o.akka.persistence.s3.journal.S3Journal
import com.typesafe.config.{ Config, ConfigFactory }

object ConfigHelper {

  def config(
      defaultResource: String,
      s3Port: Int,
      accessKeyId: String,
      secretAccessKey: String
  ): Config = {
    val configString =
      s"""
         |akka.persistence.journal.plugin = "j5ik2o.s3-journal"
         |akka.persistence.snapshot-store.plugin = "j5ik2o.s3-snapshot-store"
         |j5ik2o.s3-journal {
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
