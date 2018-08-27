name := "spaces"

version := "0.1"

scalaVersion := "2.12.6"

fork := true

scalacOptions ++= Seq(
    "-Ypartial-unification",
    "-feature",
    "-language:higherKinds",
)

val Http4sVersion = "0.18.16"

val LogbackVersion = "1.2.3"

lazy val doobieVersion = "0.5.3"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s" %% "http4s-circe" % Http4sVersion,
  "io.circe" %% "circe-generic" % "0.9.3",
  "io.circe" %% "circe-literal" % "0.9.3",
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "ch.qos.logback" % "logback-classic" % LogbackVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "mysql" % "mysql-connector-java" % "6.0.6",
  "io.github.scalapb-json" %% "scalapb-circe" % "0.3.0-M1",
  "com.github.pureconfig" %% "pureconfig" % "0.9.2",

  "org.tpolecat" %% "doobie-h2"        % "0.5.3" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

mainClass in reStart := Some("spaces.ApiServer")

