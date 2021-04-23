import sbt._

object Dependencies {

  object Versions {
    val scala211Version    = "2.11.12"
    val scala212Version    = "2.12.10"
    val scala213Version    = "2.13.1"
    val akka26Version      = "2.6.10"
    val akka25Version      = "2.5.32"
    val scalaTest32Version = "3.2.5"
    val scalaTest30Version = "3.0.8"
  }

  object akka {
    def slf4j(version: String): ModuleID            = "com.typesafe.akka" %% "akka-slf4j"             % version
    def stream(version: String): ModuleID           = "com.typesafe.akka" %% "akka-stream"            % version
    def testkit(version: String): ModuleID          = "com.typesafe.akka" %% "akka-testkit"           % version
    def persistence(version: String): ModuleID      = "com.typesafe.akka" %% "akka-persistence"       % version
    def persistenceTyped(version: String): ModuleID = "com.typesafe.akka" %% "akka-persistence-typed" % version
    def persistenceTck(version: String): ModuleID   = "com.typesafe.akka" %% "akka-persistence-tck"   % version
  }

  object iheart {
    val ficus = "com.iheart" %% "ficus" % "1.5.0"
  }

  object slf4j {
    val api: ModuleID        = "org.slf4j" % "slf4j-api"    % "1.7.30"
    val julToSlf4J: ModuleID = "org.slf4j" % "jul-to-slf4j" % "1.7.30"
  }

  object software {

    object awssdk {
      val s3 = "software.amazon.awssdk" % "s3" % "2.16.47"
    }

  }

  object j5ik2o {
    val reactiveAwsS3: ModuleID = "com.github.j5ik2o" %% "reactive-aws-s3-core" % "1.2.6"
  }

  object scalacheck {
    val scalacheck: ModuleID = "org.scalacheck" %% "scalacheck" % "1.15.2"
  }

  object scalatest {
    def scalatest(version: String): ModuleID = "org.scalatest" %% "scalatest" % version
  }

  object scala {
    val collectionCompat: ModuleID         = "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3"
    def reflect(version: String): ModuleID = "org.scala-lang"          % "scala-reflect"           % version
  }

  object logback {
    val classic: ModuleID = "ch.qos.logback" % "logback-classic" % "1.2.3"
  }

  object typesafe {
    val config: ModuleID = "com.typesafe" % "config" % "1.4.1"
  }

  object testcontainers {
    val testcontainersVersion              = "1.15.3"
    val testcontainers: ModuleID           = "org.testcontainers" % "testcontainers" % testcontainersVersion
    val testcontainersLocalStack: ModuleID = "org.testcontainers" % "localstack"     % testcontainersVersion
    val testcontainersKafka: ModuleID      = "org.testcontainers" % "kafka"          % testcontainersVersion
  }

  object dimafeng {
    val testcontainersScalaVersion   = "0.39.3"
    val testcontainerScala: ModuleID = "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion
    val testcontainerScalaScalaTest: ModuleID =
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaVersion
    //    val testcontainerScalaMsql       = "com.dimafeng" %% "testcontainers-scala-mysql" % testcontainersScalaVersion
    val testcontainerScalaKafka: ModuleID = "com.dimafeng" %% "testcontainers-scala-kafka" % testcontainersScalaVersion
    val testcontainerScalaLocalstack: ModuleID =
      "com.dimafeng" %% "testcontainers-scala-localstack" % testcontainersScalaVersion
  }

}
