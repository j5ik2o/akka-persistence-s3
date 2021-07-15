# akka-persistence-s3(support aws sdk for java v2)

[![CI](https://github.com/j5ik2o/akka-persistence-s3/workflows/CI/badge.svg)](https://github.com/j5ik2o/akka-persistence-s3/actions?query=workflow%3ACI)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/akka-persistence-s3_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/akka-persistence-s3_2.12)
[![Scaladoc](http://javadoc-badge.appspot.com/com.github.j5ik2o/akka-persistence-s3_2.12.svg?label=scaladoc)](http://javadoc-badge.appspot.com/com.github.j5ik2o/akka-persistence-s3_2.12/com/github/j5ik2o/akka/persistence/s3/index.html?javadocio=true)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fj5ik2o%2Fakka-persistence-s3.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fj5ik2o%2Fakka-persistence-s3?ref=badge_shield)

akka-persistence-s3 writes snapshot entries to AWS S3. It's non-blocking I/ O used by [aws-sdk-java-v2](https://github.com/aws/aws-sdk-java-v2).

## Supported versions:

- Java: `1.8+`
- Scala: `2.12.x` or `2.13.x` or `3.0.0`
- Akka: `2.6.x`
- AWS-SDK: `2.4.x`

## Installation

Add the following to your sbt build (2.12.x, 2.13.x, 3.0.0):

```scala
val version = "..."

libraryDependencies += Seq(
  "com.github.j5ik2o" %% "akka-persistence-s3-journal" % version,
  "com.github.j5ik2o" %% "akka-persistence-s3-snapshot" % version
)
```

## Configuration

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
      region = "..."
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


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fj5ik2o%2Fakka-persistence-s3.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fj5ik2o%2Fakka-persistence-s3?ref=badge_large)
