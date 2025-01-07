ThisBuild / scalaVersion := "2.13.15"
ThisBuild / version := "0.3.0"
ThisBuild / organization := "UCSC-AHD"

val chiselVersion = "6.6.0"

lazy val root = (project in file("."))
  .settings(
    name := "sdram_controller_gen",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel"    % chiselVersion,
      "org.scalatest"     %% "scalatest" % "3.2.16" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations"
    ),
    addCompilerPlugin(
      ("org.chipsalliance" % "chisel-plugin" % chiselVersion)
        .cross(CrossVersion.full)
    )
  )
libraryDependencies += "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % "test"
libraryDependencies += "com.typesafe.play" %% "play-json"  % "2.10.0"
enablePlugins(ScalafmtPlugin)
