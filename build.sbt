import BuildHelper._
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts
import xerial.sbt.Sonatype.sonatypeCentralHost

lazy val binCompatVersionToCompare = None

inThisBuild(
  List(
    organization := "io.github.jirihausner",
    homepage     := Some(url("https://github.com/jirihausner/zio-schema-circe")),
    scmInfo      := Some(
      ScmInfo(url("https://github.com/jirihausner/zio-schema-circe"), "git@github.com:jirihausner/zio-schema-circe.git"),
    ),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
      Developer(
        "jirihausner",
        "Jiri Hausner",
        "jiri.hausner.j@gmail.com",
        url("https://github.com/jirihausner"),
      ),
    ),
  ),
)

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
sonatypeRepository                 := "https://s01.oss.sonatype.org/service/local"

Global / onChangedBuildSource := ReloadOnSourceChanges

addCommandAlias("fmt", "all scalafmtSbt scalafmtAll;fix")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")

addCommandAlias("prepare", "fmt; fix")
addCommandAlias("lint", "fmtCheck; fixCheck")

addCommandAlias("testJVM", "zioSchemaCirceJVM/test; zioSchemaCirceJsoniterJVM/test")
addCommandAlias("testJS", "zioSchemaCirceJS/test; zioSchemaCirceJsoniterJS/test")
addCommandAlias("testNative", "zioSchemaCirceNative/test; zioSchemaCirceJsoniterNative/test")

addCommandAlias("mimaCheck", "+zioSchemaCirce/mimaReportBinaryIssues")

lazy val root = project
  .in(file("."))
  .settings(
    name                  := "zio-schema-circe",
    publish / skip        := true,
    mimaPreviousArtifacts := Set.empty,
  )
  .aggregate(
    zioSchemaCirce.jvm,
    zioSchemaCirce.js,
    zioSchemaCirce.native,
    zioSchemaCirceJsoniter.jvm,
    zioSchemaCirceJsoniter.js,
    zioSchemaCirceJsoniter.native,
  )

lazy val zioSchemaCirce =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .in(file("zio-schema-circe"))
    .enablePlugins(BuildInfoPlugin)
    .settings(stdSettings("zio-schema-circe"))
    .settings(buildInfoSettings("zio.schema.codec.circe"))
    .settings(mimaSettings(binCompatVersionToCompare, failOnProblem = true))
    .settings(
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core"            % Versions.circe,
        "io.circe" %%% "circe-generic"         % Versions.circe     % Test,
        "io.circe" %%% "circe-parser"          % Versions.circe,
        "dev.zio"  %%% "zio"                   % Versions.zio,
        "dev.zio"  %%% "zio-test"              % Versions.zio       % Test,
        "dev.zio"  %%% "zio-test-sbt"          % Versions.zio       % Test,
        "dev.zio"  %%% "zio-streams"           % Versions.zio,
        "dev.zio"  %%% "zio-schema"            % Versions.zioSchema,
        "dev.zio"  %%% "zio-schema-derivation" % Versions.zioSchema % Test,
        "dev.zio"  %%% "zio-schema-zio-test"   % Versions.zioSchema % Test,
      ),
    )
    .settings(macroDefinitionSettings)
    .settings(crossProjectSettings)
    .settings(Test / fork := crossProjectPlatform.value == JVMPlatform)
    .nativeSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.scalaJavaTime,
      ),
    )
    .jsSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time"      % Versions.scalaJavaTime,
        "io.github.cquiroz" %%% "scala-java-time-tzdb" % Versions.scalaJavaTime,
      ),
    )
    .jsSettings(
      scalaJSLinkerConfig ~= { _.withOptimizer(false) },
      scalaJSUseMainModuleInitializer := true,
    )

lazy val zioSchemaCirceJsoniter =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .in(file("zio-schema-circe-jsoniter"))
    .enablePlugins(BuildInfoPlugin)
    .settings(stdSettings("zio-schema-circe-jsoniter"))
    .settings(buildInfoSettings("zio.schema.codec.circe.jsoniter"))
    .settings(mimaSettings(binCompatVersionToCompare, failOnProblem = true))
    .settings(
      libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-circe" % Versions.jsoniter,
    )
    .settings(macroDefinitionSettings)
    .settings(crossProjectSettings)
    .settings(Test / fork := crossProjectPlatform.value == JVMPlatform)
    .nativeSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.scalaJavaTime,
      ),
    )
    .jsSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time"      % Versions.scalaJavaTime,
        "io.github.cquiroz" %%% "scala-java-time-tzdb" % Versions.scalaJavaTime,
      ),
    )
    .jsSettings(
      scalaJSLinkerConfig ~= { _.withOptimizer(false) },
      scalaJSUseMainModuleInitializer := true,
    )
    .dependsOn(zioSchemaCirce, zioSchemaCirce % "test->test")
