import Dependencies.Versions._
import Dependencies._
import sbt._

ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)

def crossScalacOptions(scalaVersion: String): Seq[String] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3L, _)) =>
      Seq(
        "-source:3.0-migration",
        "-Xignore-scala2-macros"
      )
    case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
      Seq(
        "-Ydelambdafy:method",
        "-target:jvm-1.8",
        "-Yrangepos",
        "-Ywarn-unused"
      )
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
  crossScalaVersions ++= Seq(scala212Version, scala213Version, scala3Version),
  scalacOptions ++= (Seq(
    "-unchecked",
    "-feature",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:_"
  ) ++ crossScalacOptions(scalaVersion.value)),
  resolvers ++= Seq(
    "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
    "Seasar Repository" at "https://maven.seasar.org/maven2/",
    "jitpack" at "https://jitpack.io"
  ),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  Test / fork := true,
  Test / publishArtifact := false,
  Test / parallelExecution := false,
  packageDoc / publishArtifact := false
)

lazy val test = (project in file("test"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-test",
    libraryDependencies ++= Seq(
      typesafe.config,
      software.awssdk.s3,
      scalatest.scalatest
    ),
    libraryDependencies ++= Seq(
      j5ik2o.dockerControllerScalaScalatest,
      j5ik2o.dockerControllerScalaMinio
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3L, _))                              => Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 13 => Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 12 => Seq(scala.collectionCompat)
      }
    }
  )

lazy val base = (project in file("base"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-base",
    libraryDependencies ++= Seq(
      slf4j.api,
      software.awssdk.s3,
      logback.classic % Test
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, _)) =>
          Seq(scala.reflect(scalaVersion.value))
        case _ =>
          Seq.empty
      }
    },
    libraryDependencies ++= Seq(
      iheart.ficus,
      akka.slf4j,
      akka.stream,
      akka.testkit % Test
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3L, _)) =>
          Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(scala.collectionCompat)
      }
    }
  )
  .dependsOn(test % "test->compile")

lazy val snapshot = (project in file("snapshot"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-snapshot",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test
    ),
    libraryDependencies ++= Seq(
      akka.persistence,
      akka.persistenceTck % Test
    ).map(_.cross(CrossVersion.for3Use2_13))
  )
  .dependsOn(base % "test->test;compile->compile")

lazy val journal = (project in file("journal"))
  .settings(coreSettings)
  .settings(
    name := "akka-persistence-s3-journal",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test
    ),
    libraryDependencies ++= Seq(
      akka.persistence,
      akka.persistenceTck % Test
    ).map(_.cross(CrossVersion.for3Use2_13))
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
    libraryDependencies ++= Seq(
      akka.slf4j,
      akka.persistenceTyped
    ).map(_.cross(CrossVersion.for3Use2_13))
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
