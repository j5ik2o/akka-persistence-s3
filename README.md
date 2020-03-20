# akka-persistence-s3

[![CircleCI](https://circleci.com/gh/j5ik2o/akka-persistence-s3/tree/master.svg?style=shield&circle-token=d764dfd21bb20c816689c236607ac1426a72b581)](https://circleci.com/gh/j5ik2o/akka-persistence-s3/tree/master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/akka-persistence-s3_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/akka-persistence-s3_2.12)
[![Scaladoc](http://javadoc-badge.appspot.com/com.github.j5ik2o/akka-persistence-s3_2.12.svg?label=scaladoc)](http://javadoc-badge.appspot.com/com.github.j5ik2o/akka-persistence-s3_2.12/com/github/j5ik2o/akka/persistence/s3/index.html?javadocio=true)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

akka-persistence-s3 writes snapshot entries to AWS S3.

## Supported versions:

- Java: `1.8+`
- Scala: `2.12.x` or `2.13.x` 
- Akka: `2.6.x+`
- AWS-SDK: `2.4.x`

## Installation

Add the following to your sbt build (2.12.x, 2.13.x):

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
    bucket-name-resolver-class-name = "com.github.j5ik2o.akka.persistence.s3.resolver.BucketNameResolver$PersistenceId"
    key-converter-class-name = "com.github.j5ik2o.akka.persistence.s3.resolver.KeyConverter$PersistenceId"
    s3-client {
      # Set the following as needed
      access-key-id = ""
      secret-access-key = ""
      endpoint = ""
      s3-options {
        path-style-access-enabled = true
      }
    }
  }
}
```
