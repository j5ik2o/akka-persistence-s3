package com.github.j5ik2o.akka.persistence.s3.snapshot

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

class S3SnapshotStoreSpec
    extends SnapshotStoreSpec(
      ConfigFactory
        .parseString(
          """
    |akka.persistence.snapshot-store.plugin = "s3-snapshot-store"
    |j5ik2.s3-snapshot-store {
    |  s3-client {
    |    aws-access-key-id = "test"
    |    aws-secret-access-key = "test"
    |    region = "us-west-2"
    |    endpoint = "http://localhost:4567"
    |    options {
    |      path-style-access = true
    |    }
    |  }
    |}
  """.stripMargin
        )
        .withFallback(ConfigFactory.load())
    ) {}
