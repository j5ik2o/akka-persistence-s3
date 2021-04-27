import Dependencies._
import Versions._

def crossScalacOptions(scalaVersion: String): Seq[String] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
      Seq.empty
    case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
      Seq("-Yinline-warnings")
  }

val coreSettings = Seq(
  organization := "com.github.j5ik2o",
  homepage := Some(url("https://github.com/j5ik2o/akka-persistence-s3")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      id = "j5ik2o",
      name = "Junichi Kato",
      email = "j5ik2o@gmail.com",
      url = url("https://blog.j5ik2o.me")
    )
  ),
  scalaVersion := scala213Version,
  crossScalaVersions ++= Seq(scala212Version, scala213Version),
  scalacOptions ++=
    Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:_",
      "-target:jvm-1.8",
      "-Yrangepos",
      "-Ywarn-unused"
    ) ++ crossScalacOptions(scalaVersion.value),
  resolvers ++= Seq(
    "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
    "Seasar Repository" at "https://maven.seasar.org/maven2/",
    "jitpack" at "https://jitpack.io"
  ),
  ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  Test / fork := true,
  Test / publishArtifact := false,
  Test / parallelExecution := false
)

lazy val test = (project in file("test"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-test",
    libraryDependencies ++= Seq(
      typesafe.config,
      software.awssdk.s3,
//      testcontainers.testcontainers,
//      dimafeng.testcontainerScalaScalaTest,
      j5ik2o.dockerControllerScalaScalatest,
      j5ik2o.dockerControllerScalaMinio
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 => Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 12 => Seq(scala.collectionCompat)
        case Some((2L, scalaMajor)) if scalaMajor == 11 => Seq(scala.collectionCompat)
      }
    }
  )

lazy val base = (project in file("base"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-base",
    libraryDependencies ++= Seq(
      scala.reflect(scalaVersion.value),
      iheart.ficus,
      slf4j.api,
      software.awssdk.s3,
      scalacheck.scalacheck                 % Test,
      logback.classic                       % Test,
      j5ik2o.dockerControllerScalaScalatest % Test
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq(
            akka.slf4j(akka26Version),
            akka.stream(akka26Version),
            akka.testkit(akka26Version)             % Test,
            scalatest.scalatest(scalaTest32Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            scala.collectionCompat,
            akka.slf4j(akka26Version),
            akka.stream(akka26Version),
            akka.testkit(akka26Version)             % Test,
            scalatest.scalatest(scalaTest32Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 11 =>
          Seq(
            scala.collectionCompat,
            akka.slf4j(akka25Version),
            akka.stream(akka25Version),
            akka.testkit(akka25Version)             % Test,
            scalatest.scalatest(scalaTest30Version) % Test
          )
      }
    }
  )
  .dependsOn(test % "test->compile")

lazy val snapshot = (project in file("snapshot"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-snapshot",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq(
            akka.persistence(akka26Version),
            akka.persistenceTck(akka26Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            akka.persistence(akka26Version),
            akka.persistenceTck(akka26Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 11 =>
          Seq(
            akka.persistence(akka25Version),
            akka.persistenceTck(akka25Version) % Test
          )
      }
    }
  )
  .dependsOn(base % "test->test;compile->compile")

lazy val journal = (project in file("journal"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-journal",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq(
            akka.persistence(akka26Version),
            akka.persistenceTck(akka26Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            akka.persistence(akka26Version),
            akka.persistenceTck(akka26Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 11 =>
          Seq(
            akka.persistence(akka25Version),
            akka.persistenceTck(akka25Version) % Test
          )
      }
    }
  )
  .dependsOn(base % "test->test;compile->compile", snapshot % "test->comple")

lazy val benchmark = (project in file("benchmark"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-dynamodb-benchmark",
    publish / skip := true,
    libraryDependencies ++= Seq(
      logback.classic,
      slf4j.api,
      slf4j.julToSlf4J
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
          Seq(
            akka.slf4j(akka26Version),
            akka.persistenceTyped(akka26Version)
          )
        case Some((2L, scalaMajor)) if scalaMajor == 11 =>
          Seq(
            akka.slf4j(akka25Version),
            akka.persistence(akka25Version)
          )
      }
    }
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(test, journal, snapshot)

lazy val root = (project in file("."))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-root",
    publish / skip := true
  )
  .aggregate(base, journal, snapshot)

// --- Custom commands
addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
