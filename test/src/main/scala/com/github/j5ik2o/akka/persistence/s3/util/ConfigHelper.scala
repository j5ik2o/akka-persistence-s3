package com.github.j5ik2o.akka.persistence.s3.util

import com.typesafe.config.{ Config, ConfigFactory }

object ConfigHelper {

  def config(
      defaultResource: Option[String],
      testTimeFactor: Double,
      s3Host: String,
      s3Port: Int,
      accessKeyId: String,
      secretAccessKey: String,
      bucketName: Option[String]
  ): Config = {
    val configString =
      s"""
         |akka.test.timeFactor = $testTimeFactor
         |akka.persistence.journal.plugin = "j5ik2o.s3-journal"
         |akka.persistence.snapshot-store.plugin = "j5ik2o.s3-snapshot-store"
         |j5ik2o.s3-journal {
         |  ${if (bucketName.isEmpty) "" else s"""bucket-name = "${bucketName}""""}
         |  s3-client {
         |    access-key-id = "${accessKeyId}"
         |    secret-access-key = "${secretAccessKey}"
         |    endpoint = "http://$s3Host:$s3Port"
         |    s3-options {
         |      path-style-access-enabled = true
         |    }
         |  }
         |}
         |
         |j5ik2o.s3-snapshot-store {
         |  ${if (bucketName.isEmpty) "" else s"""bucket-name = "${bucketName}""""}
         |  s3-client {
         |    access-key-id = "${accessKeyId}"
         |    secret-access-key = "${secretAccessKey}"
         |    endpoint = "http://$s3Host:$s3Port"
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
      .withFallback(
        defaultResource.fold(ConfigFactory.load())(ConfigFactory.load)
      )
    // println(ConfigRenderUtils.renderConfigToString(config))
    config
  }
}
