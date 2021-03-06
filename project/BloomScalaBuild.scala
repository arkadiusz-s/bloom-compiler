import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager.packageArchetype


object BuildSettings {
  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "edu.berkeley.cs.boom",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.10.3",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("releases"),
      Resolver.typesafeRepo("releases")
    ),
    parallelExecution in Test := false
  )
}


object BloomScalaBuild extends Build {

  import BuildSettings._

  lazy val root = Project(
    "root",
    file("."),
    settings = buildSettings ++ Seq(
      run <<= run in Compile in compiler,
      runMain <<= runMain in Compile in compiler,
      console <<= console in Compile in compiler)
  ) aggregate(compiler)

  lazy val compiler = Project(
    "bloom-compiler",
    file("compiler"),
    settings = packageArchetype.java_application ++ buildSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
        "org.slf4j" % "slf4j-log4j12" % "1.7.5",
        "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
        "com.googlecode.kiama" % "kiama_2.10" % "1.5.2",
        "com.quantifind" %% "sumac" % "0.2.3",
        "org.typelevel" %% "scalaz-contrib-210" % "0.1.5"
      )
    )
  )

}
