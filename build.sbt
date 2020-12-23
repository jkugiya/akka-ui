name := "akka-ui"

version := "0.5.0"

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.3"
lazy val supportedScalaVersions = List(scala213, scala212)

scalaVersion := scala213
crossScalaVersions := supportedScalaVersions

enablePlugins(ScalaJSPlugin)

libraryDependencies ++= Seq(
  "org.akka-js" %%% "akkajsactor" % "2.2.6.9",
  "org.akka-js" %%% "akkajsactorstream" % "2.2.6.9",
  "org.scala-js" %%% "scalajs-dom" % "1.1.0",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

organization := "net.pishen"

licenses += "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")

homepage := Some(url("https://github.com/pishen/akka-ui"))

pomExtra := (
  <scm>
    <url>https://github.com/pishen/akka-ui.git</url>
    <connection>scm:git:git@github.com:pishen/akka-ui.git</connection>
  </scm>
  <developers>
    <developer>
      <id>pishen</id>
      <name>Pishen Tsai</name>
    </developer>
  </developers>
)
