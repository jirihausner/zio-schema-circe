import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys._
import org.snakeyaml.engine.v2.api.{Load, LoadSettings}
import sbt._
import sbt.Keys._
import sbtbuildinfo._
import sbtbuildinfo.BuildInfoKeys._
import sbtcrossproject.CrossPlugin.autoImport._
import sbtdynver.DynVerPlugin.autoImport.previousStableVersion
import scalafix.sbt.ScalafixPlugin.autoImport._
import scalanativecrossproject.NativePlatform

import java.util.{List => JList, Map => JMap}
import scala.jdk.CollectionConverters._
import scala.scalanative.build.{GC, Mode}
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig

object BuildHelper {

  private val versions: Map[String, String] = {
    val doc  = new Load(LoadSettings.builder().build())
      .loadFromReader(scala.io.Source.fromFile(".github/workflows/ci.yml").bufferedReader())
    val yaml = doc.asInstanceOf[JMap[String, JMap[String, JMap[String, JMap[String, JMap[String, JList[String]]]]]]]
    val list = yaml.get("jobs").get("build").get("strategy").get("matrix").get("scala").asScala
    list.map(v => (v.split('.').take(2).mkString("."), v)).toMap
  }

  val Scala212: String = versions("2.12")
  val Scala213: String = versions("2.13")
  val Scala3: String   = versions("3.3")

  val BinCompatVersionToCompare: Option[String] = Some("0.3.2")

  object Versions {

    val circe           = "0.14.10"
    val circeDerivation = "0.13.0-M5"
    val jsoniter        = "2.33.2"
    val scalaJavaTime   = "2.6.0"
    val zio             = "2.1.17"
    val zioSchema       = "1.7.0"
  }

  def compilerOptions(scalaVersion: String, optimize: Boolean) = {
    val stdOptions = Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-language:existentials",
      "-language:implicitConversions",
    ) ++ {
      if (sys.env.contains("CI")) {
        Seq("-Xfatal-warnings")
      } else {
        Seq()
      }
    }

    val std2xOptions = Seq(
      "-language:higherKinds",
      "-explaintypes",
      "-Yrangepos",
      "-Xlint:_,-missing-interpolator,-type-parameter-shadow,-infer-any",
      "-Ypatmat-exhaust-depth",
      "40",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xsource:3.0",
    )

    val optimizerOptions =
      if (optimize)
        Seq(
          "-opt:l:inline",
        )
      else Seq.empty

    val extraOptions = CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _))  =>
        Seq(
          "-Xignore-scala2-macros",
          "-Ykind-projector",
        )
      case Some((2, 13)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused",
          "-Ymacro-annotations",
          "-Ywarn-macros:after",
        ) ++ std2xOptions ++ optimizerOptions
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Yno-adapted-args",
          "-Ypartial-unification",
          "-Ywarn-extra-implicit",
          "-Ywarn-inaccessible",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused-import",
          "-Wconf:cat=deprecation:silent",
          "-Wconf:cat=unused-nowarn:s",
        ) ++ std2xOptions ++ optimizerOptions
      case _             => Seq.empty
    }

    stdOptions ++ extraOptions
  }

  val dottySettings = Seq(
    scalacOptions --= {
      if (scalaVersion.value == Scala3)
        Seq("-Xfatal-warnings")
      else
        Seq()
    },
  )

  def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*): Seq[File] =
    for {
      platform <- List("shared", platform)
      version  <- "scala" :: versions.toList.map("scala-" + _)
      result = baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
      if result.exists
    } yield result

  def crossPlatformSources(scalaVersion: String, platform: String, conf: String, baseDir: File): Seq[sbt.File] = {
    val versions = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 11)) =>
        List("2.11", "2.11+", "2.11-2.12", "2.x")
      case Some((2, 12)) =>
        List("2.12", "2.11+", "2.12+", "2.11-2.12", "2.12-2.13", "2.x")
      case Some((2, 13)) =>
        List("2.13", "2.11+", "2.12+", "2.13+", "2.12-2.13", "2.x")
      case _             =>
        List()
    }
    platformSpecificSources(platform, conf, baseDir)(versions: _*)
  }

  lazy val crossProjectSettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "main",
        baseDirectory.value,
      )
    },
    Test / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "test",
        baseDirectory.value,
      )
    },
    nativeConfig ~= { cfg =>
      val os = System.getProperty("os.name").toLowerCase
      // For some unknown reason, we can't run the test suites in debug mode on MacOS
      if (os.contains("mac")) cfg.withMode(Mode.releaseFast)
      else cfg.withGC(GC.boehm) // See https://github.com/scala-native/scala-native/issues/4032
    },
    scalacOptions += {
      if (crossProjectPlatform.value == NativePlatform)
        "-P:scalanative:genStaticForwardersForNonTopLevelObjects"
      else ""
    },
    Test / fork := crossProjectPlatform.value == JVMPlatform, // set fork to `true` on JVM to improve log readability, JS and Native need `false`
  )

  def macroDefinitionSettings = Seq(
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++= {
      if (scalaVersion.value == Scala3) Seq()
      else
        Seq(
          "org.scala-lang" % "scala-reflect"  % scalaVersion.value % Provided,
          "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided,
        )
    },
  )

  def buildInfoSettings(packageName: String) = Seq(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := packageName,
  )

  def stdSettings(projectName: String) =
    Seq(
      name                          := projectName,
      crossScalaVersions            := Seq(Scala213, Scala212, Scala3),
      ThisBuild / scalaVersion      := Scala213,
      scalacOptions ++= compilerOptions(scalaVersion.value, optimize = !isSnapshot.value),
      libraryDependencies ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, _)) =>
            Seq(
              compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.3").cross(CrossVersion.full)),
              compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
            )
          case _            => List.empty
        }
      },
      libraryDependencies ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, x)) if x <= 12 =>
            Seq(
              compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)),
            )
          case _                       => List.empty
        }
      },
      ThisBuild / semanticdbEnabled := scalaVersion.value != Scala3,
      ThisBuild / semanticdbOptions += "-P:semanticdb:synthetics:on",
      ThisBuild / semanticdbVersion := scalafixSemanticdb.revision,
      ThisBuild / scalafixDependencies ++= List(
        "com.github.vovapolu"                      %% "scaluzzi" % "0.1.23",
        "io.github.ghostbuster91.scalafix-unified" %% "unified"  % "0.0.9",
      ),
      Test / parallelExecution      := !sys.env.contains("CI"),
      incOptions ~= (_.withLogRecompileOnMacro(true)),
      autoAPIMappings               := true,
      testFrameworks                := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      mimaCheckDirection            := "backward",
      mimaFailOnProblem             := true,
      mimaFailOnNoPrevious          := false,
      mimaPreviousArtifacts         := {
        BinCompatVersionToCompare match {
          case Some(version) => Set(organization.value %% name.value % version)
          case None          =>
            Set.empty
        }
      },
      mimaReportSignatureProblems   := true,
    )
}
