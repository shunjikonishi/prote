name := """test-proxy"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  "com.ning" % "async-http-client" % "1.8.14",
  "jp.co.flect" % "flectCommon" % "1.2.2",
  "net.debasishg" %% "redisclient" % "2.13"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

resolvers ++= Seq(
  "FLECT Maven Repository on Github" at "http://flect.github.io/maven-repo/"
)

