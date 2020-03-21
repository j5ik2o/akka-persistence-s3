val scala211Version = "2.11.12"
val scala212Version = "2.12.10"
val scala213Version = "2.13.1"
val akka26Version   = "2.6.4"
val akka25Version   = "2.5.30"

val coreSettings = Seq(
  sonatypeProfileName := "com.github.j5ik2o",
  organization := "com.github.j5ik2o",
  scalaVersion := scala211Version,
  crossScalaVersions ++= Seq(scala211Version, scala212Version, scala213Version),
  scalacOptions ++= {
    Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:_",
      "-target:jvm-1.8"
    ) ++ {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
          Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
          Seq("-Yinline-warnings")
      }
    }
  },
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>https://github.com/j5ik2o/akka-persistence-s3</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
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
  },
  resolvers ++= Seq(
      "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
      "Seasar Repository" at "https://maven.seasar.org/maven2/",
      "jitpack" at "https://jitpack.io"
    ),
  libraryDependencies ++= Seq(
      "org.scala-lang"    % "scala-reflect"                % scalaVersion.value,
      "com.iheart"        %% "ficus"                       % "1.4.7",
      "org.slf4j"         % "slf4j-api"                    % "1.7.25",
      "com.github.j5ik2o" %% "reactive-aws-s3-core"        % "1.1.7",
      "org.scalacheck"    %% "scalacheck"                  % "1.14.3" % Test,
      "ch.qos.logback"    % "logback-classic"              % "1.2.3" % Test,
      "com.whisk"         %% "docker-testkit-scalatest"    % "0.9.9" % Test,
      "com.whisk"         %% "docker-testkit-impl-spotify" % "0.9.9" % Test
    ) ++ {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq(
            "com.typesafe.akka" %% "akka-slf4j"           % akka26Version,
            "com.typesafe.akka" %% "akka-persistence"     % akka26Version,
            "com.typesafe.akka" %% "akka-testkit"         % akka26Version % Test,
            "com.typesafe.akka" %% "akka-persistence-tck" % akka26Version % Test,
            "org.scalatest"     %% "scalatest"            % "3.1.1" % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4",
            "com.typesafe.akka"      %% "akka-slf4j"              % akka26Version,
            "com.typesafe.akka"      %% "akka-persistence"        % akka26Version,
            "com.typesafe.akka"      %% "akka-testkit"            % akka26Version % Test,
            "com.typesafe.akka"      %% "akka-persistence-tck"    % akka26Version % Test,
            "org.scalatest"          %% "scalatest"               % "3.1.1" % Test
          )
        case Some((2L, scalaMajor)) if scalaMajor == 11 =>
          Seq(
            "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4",
            "com.typesafe.akka"      %% "akka-slf4j"              % akka25Version,
            "com.typesafe.akka"      %% "akka-persistence"        % akka25Version,
            "com.typesafe.akka"      %% "akka-testkit"            % akka25Version % Test,
            "com.typesafe.akka"      %% "akka-persistence-tck"    % akka25Version % Test,
            "org.scalatest"          %% "scalatest"               % "3.1.1" % Test
          )
      }
    },
  parallelExecution in Test := false
)

lazy val `root` = (project in file("."))
  .settings(coreSettings)
  .settings(name := "akka-persistence-s3")
