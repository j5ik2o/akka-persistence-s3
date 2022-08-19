import sbt._

object Dependencies {

  object Versions {
    val scala212Version  = "2.12.16"
    val scala213Version  = "2.13.8"
    val scala3Version    = "3.1.3"
    val akkaVersion      = "2.6.19"
    val scalaTestVersion = "3.2.13"
  }

  object akka {
    def slf4j: ModuleID       = "com.typesafe.akka" %% "akka-slf4j"       % Versions.akkaVersion
    def stream: ModuleID      = "com.typesafe.akka" %% "akka-stream"      % Versions.akkaVersion
    def testkit: ModuleID     = "com.typesafe.akka" %% "akka-testkit"     % Versions.akkaVersion
    def persistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % Versions.akkaVersion
    def persistenceTyped: ModuleID =
      "com.typesafe.akka" %% "akka-persistence-typed" % Versions.akkaVersion
    def persistenceTck: ModuleID = "com.typesafe.akka" %% "akka-persistence-tck" % Versions.akkaVersion
  }

  object iheart {
    val ficus = "com.iheart" %% "ficus" % "1.5.2"
  }

  object slf4j {
    val api: ModuleID        = "org.slf4j" % "slf4j-api"    % "1.7.36"
    val julToSlf4J: ModuleID = "org.slf4j" % "jul-to-slf4j" % "1.7.30"
  }

  object software {

    object awssdk {
      val s3 = "software.amazon.awssdk" % "s3" % "2.17.257"
    }

  }

  object j5ik2o {
    val version = "1.1.1"
    val dockerControllerScalaScalatest =
      "com.github.j5ik2o" %% "docker-controller-scala-scalatest" % version
    val dockerControllerScalaMinio =
      "com.github.j5ik2o" %% "docker-controller-scala-minio" % version
  }

  object scalatest {
    def scalatest: ModuleID = "org.scalatest" %% "scalatest" % Versions.scalaTestVersion
  }

  object scala {
    val collectionCompat: ModuleID         = "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1"
    def reflect(version: String): ModuleID = "org.scala-lang"          % "scala-reflect"           % version
  }

  object logback {
    val classic: ModuleID = "ch.qos.logback" % "logback-classic" % "1.2.11"
  }

  object typesafe {
    val config: ModuleID = "com.typesafe" % "config" % "1.4.1"
  }

}
