name := "sbt-swagger-models"
organization := "io.grhodes.sbt"

version := "1.4.2"

scalaVersion := "2.12.20"

enablePlugins(SbtPlugin)

libraryDependencies ++= Seq(
  "io.swagger.codegen.v3" % "swagger-codegen" % "3.0.68",
  "io.grhodes" %% "simple-scala-generator" % "1.4.1",
  "org.scalactic" %% "scalactic" % "3.2.19" % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

scalacOptions ++= List("-unchecked")

githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource
  .Environment("GITHUB_TOKEN")
githubOwner := "AYLIEN"
githubRepository := "sbt-swagger-models"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

console / initialCommands := """import io.grhodes.sbt.swagger.models._"""

// set up 'scripted; sbt plugin for testing sbt plugins
scriptedBufferLog := false
scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  "-XX:MaxPermSize=256M",
  s"-Dplugin.version=${version.value}"
)
