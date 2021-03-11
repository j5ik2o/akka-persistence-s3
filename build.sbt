import Dependencies._
import Versions._

def crossScalacOptions(scalaVersion: String): Seq[String] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
      Seq.empty
    case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
      Seq("-Yinline-warnings")
  }

lazy val deploySettings = Seq(
  sonatypeProfileName := "com.github.j5ik2o",
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>https://github.com/j5ik2o/akka-persistence-s3</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:j5ik2o/akka-persistence-s3.git</url>
        <connection>scm:git:github.com/j5ik2o/akka-persistence-s3</connection>
        <developerConnection>scm:git:git@github.com:j5ik2o/akka-persistence-s3.git</developerConnection>
      </scm>
      <developers>
        <developer>
          <id>j5ik2o</id>
          <name>Junichi Kato</name>
        </developer>
      </developers>
  },
  publishTo := sonatypePublishToBundle.value,
  credentials := {
    val ivyCredentials = (baseDirectory in LocalRootProject).value / ".credentials"
    val gpgCredentials = (baseDirectory in LocalRootProject).value / ".gpgCredentials"
    Credentials(ivyCredentials) :: Credentials(gpgCredentials) :: Nil
  }
)

val coreSettings = Seq(
  organization := "com.github.j5ik2o",
  scalaVersion := scala213Version,
  crossScalaVersions ++= Seq(scala211Version, scala212Version, scala213Version),
  scalacOptions ++=
    Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:_",
      "-target:jvm-1.8"
    ) ++ crossScalacOptions(scalaVersion.value),
  resolvers ++= Seq(
      "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
      "Seasar Repository" at "https://maven.seasar.org/maven2/",
      "jitpack" at "https://jitpack.io"
    ),
  fork in Test := true,
  parallelExecution in Test := false,
//  Global / concurrentRestrictions += Tags.limit(Tags.Test, 1),
  scalafmtOnCompile in ThisBuild := true
)

lazy val test = (project in file("test"))
  .settings(coreSettings)
  .settings(deploySettings)
  .settings(
    name := "akka-persistence-s3-test",
    libraryDependencies ++= Seq(
        typesafe.config,
        j5ik2o.reactiveAwsS3,
        testcontainers.testcontainers,
        dimafeng.testcontainerScalaScalaTest
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
  .settings(deploySettings)
  .settings(
    name := "akka-persistence-s3-base",
    libraryDependencies ++= Seq(
        scala.reflect(scalaVersion.value),
        iheart.ficus,
        slf4j.api,
        j5ik2o.reactiveAwsS3,
        scalacheck.scalacheck                   % Test,
        logback.classic                         % Test,
        testcontainers.testcontainers           % Test,
        testcontainers.testcontainersLocalStack % Test,
        dimafeng.testcontainerScalaScalaTest    % Test,
        dimafeng.testcontainerScalaLocalstack   % Test
      ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq(
            akka.slf4j(akka26Version),
            akka.stream(akka26Version),
            akka.testkit(akka26Version)              % Test,
            scalatest.scalatest(scalaTest311Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            scala.collectionCompat,
            akka.slf4j(akka26Version),
            akka.stream(akka26Version),
            akka.testkit(akka26Version)              % Test,
            scalatest.scalatest(scalaTest311Version) % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 11 =>
          Seq(
            scala.collectionCompat,
            akka.slf4j(akka25Version),
            akka.stream(akka25Version),
            akka.testkit(akka25Version)              % Test,
            scalatest.scalatest(scalaTest308Version) % Test
          )
      }
    }
  )
  .dependsOn(test % "test->compile")

lazy val snapshot = (project in file("snapshot"))
  .settings(coreSettings)
  .settings(deploySettings)
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
  .settings(deploySettings)
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
  .settings(deploySettings)
  .settings(
    name := "akka-persistence-dynamodb-benchmark",
    skip in publish := true,
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
  .settings(deploySettings)
  .settings(
    name := "akka-persistence-s3-root",
    skip in publish := true
  )
  .aggregate(base, journal, snapshot)
