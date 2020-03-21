# akka-persistence-s3(support aws sdk for java v2)

[![CircleCI](https://circleci.com/gh/j5ik2o/akka-persistence-s3/tree/master.svg?style=shield&circle-token=d764dfd21bb20c816689c236607ac1426a72b581)](https://circleci.com/gh/j5ik2o/akka-persistence-s3/tree/master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/akka-persistence-s3_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/akka-persistence-s3_2.12)
[![Scaladoc](http://javadoc-badge.appspot.com/com.github.j5ik2o/akka-persistence-s3_2.12.svg?label=scaladoc)](http://javadoc-badge.appspot.com/com.github.j5ik2o/akka-persistence-s3_2.12/com/github/j5ik2o/akka/persistence/s3/index.html?javadocio=true)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

akka-persistence-s3 writes snapshot entries to AWS S3.aws-sdk-java-v2.  It's non-blocking I/ O used by [aws-sdk-java-v2](https://github.com/aws/aws-sdk-java-v2).

## Supported versions:

- Java: `1.8+`
- Scala: `2.11.x` or `2.12.x` or `2.13.x` 
- Akka: `2.5.x`(Scala 2.11 only), `2.6.x`(Scala 2.12, 2.13)
- AWS-SDK: `2.4.x`

## Installation

Add the following to your sbt build (2.11.x, 2.12.x, 2.13.x):

```scala
resolvers += "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/"

val version = "..."

libraryDependencies += Seq(
  "com.github.j5ik2o" %% "akka-persistence-s3" % version
)
```

## Configration

The minimum necessary settings are as follows.

```
j5ik2o {
  s3-snapshot-store {
    # if need to resolve a static value
    # bucket-name = "..."
    # if need to resolve a dynamic value
    bucket-name-resolver-class-name = "com.github.j5ik2o.akka.persistence.s3.resolver.BucketNameResolver$PersistenceId"
    key-converter-class-name = "com.github.j5ik2o.akka.persistence.s3.resolver.KeyConverter$PersistenceId"
    # if need to resolve a static value
    # path-prefix = "..."
    # if need to resolve a dynamic value
    path-prefix-resolver-class-name = "com.github.j5ik2o.akka.persistence.s3.resolver.PathPrefixResolver$PersistenceId"
    extension-name = "snapshot"
    max-load-attempts = 3
    s3-client {
      # Set the following as needed
      access-key-id = "..."
      secret-access-key = "..."
      endpoint = "..."
      max-concurrency = 128
      max-pending-connection-acquires = ?
      read-timeout = 3 s
      write-timeout = 3 s
      connection-timeout = 3 s
      connection-acquisition-timeout = 3 s
      connection-time-to-live = 3 s
      max-idle-connection-timeout = 3 s
      use-connection-reaper = true
      threads-of-event-loop-group = 32
      user-http2 = true
      max-http2-streams = 32
      batch-get-item-limit = 100
      batch-write-item-limit = 25
      s3-options {
        dualstack-enabled = false
        accelerate-mode-enabled = false
        path-style-access-enabled = true
        checksum-validation-enabled = false
        chunked-encoding-enabled = false
        use-arn-region-enabled = false
      }
    }
  }
}
```
