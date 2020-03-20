package com.github.j5ik2o.akka.persistence.s3.snapshot

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{
  Delete,
  DeleteObjectsRequest,
  ListObjectsRequest,
  ObjectIdentifier
}

import scala.jdk.CollectionConverters._
class S3SnapshotStoreSpec
    extends SnapshotStoreSpec(
      ConfigFactory
        .parseString(s"""
    |akka.persistence.snapshot-store.plugin = "j5ik2o.s3-snapshot-store"
    |j5ik2o.s3-snapshot-store {
    |  s3-client {
    |    access-key-id = "AKIAVVQYQXHEVJG3YNRS"
    |    secret-access-key = "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"
    |    # endpoint = "http://127.0.0.1:9000"
    |    path-style-access-enabled = true
    |  }
    |}
  """.stripMargin)
        .withFallback(ConfigFactory.load())
    )
    with BeforeAndAfterAll {

  val bucketName = "j5ik2o.akka-persistence-s3"
  val javaS3Client: S3AsyncClient =
    S3AsyncClient
      .builder()
      .credentialsProvider(
        StaticCredentialsProvider
          .create(
            AwsBasicCredentials.create(
              sys.env("AWS_ACCESS_KEY_ID"),
              sys.env("AWS_SECRET_ACCESS_KEY")
            )
          )
      )
      .build()

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    val s3objects = javaS3Client
      .listObjects(ListObjectsRequest.builder().bucket(bucketName).build())
      .get()
      .contents()
      .asScala
    val keyObjectIdentities = s3objects
      .map { o =>
        ObjectIdentifier.builder().key(o.key()).build()
      }
    val delete = Delete.builder().objects(keyObjectIdentities.asJava).build()
    val deleteRequest =
      DeleteObjectsRequest.builder().bucket(bucketName).delete(delete).build()

    javaS3Client.deleteObjects(deleteRequest).get()
  }

  protected override def afterAll(): Unit = {
    // s3.shutdown
    super.afterAll()
  }
}
