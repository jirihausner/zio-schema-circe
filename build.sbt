import BuildHelper._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts

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
    core.jvm,
    core.js,
    core.native,
    zioSchemaCirce.jvm,
    zioSchemaCirce.js,
    zioSchemaCirce.native,
    zioSchemaCirceJsoniter.jvm,
    zioSchemaCirceJsoniter.js,
    zioSchemaCirceJsoniter.native,
  )

lazy val core =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .in(file("core"))
    .enablePlugins(BuildInfoPlugin)
    .settings(stdSettings("zio-schema-circe-core"))
    .settings(buildInfoSettings("zio.schema.codec.circe"))
    .settings(dottySettings)
    .settings(
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core"  % Versions.circe,
        "dev.zio"  %%% "zio"         % Versions.zio,
        "dev.zio"  %%% "zio-streams" % Versions.zio,
        "dev.zio"  %%% "zio-schema"  % Versions.zioSchema,
      ),
    )
    .settings(
      mimaPreviousArtifacts := Set.empty,
      mimaBinaryIssueFilters ++= Seq(
        ProblemFilters.exclude[Problem]("zio.schema.codec.circe.internal.*"),
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

lazy val zioSchemaCirce =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .in(file("zio-schema-circe"))
    .enablePlugins(BuildInfoPlugin)
    .settings(stdSettings("zio-schema-circe"))
    .settings(buildInfoSettings("zio.schema.codec.circe"))
    .settings(dottySettings)
    .settings(
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-generic"         % Versions.circe     % Test,
        "io.circe" %%% "circe-parser"          % Versions.circe,
        "dev.zio"  %%% "zio-test"              % Versions.zio       % Test,
        "dev.zio"  %%% "zio-test-sbt"          % Versions.zio       % Test,
        "dev.zio"  %%% "zio-schema-derivation" % Versions.zioSchema % Test,
        "dev.zio"  %%% "zio-schema-zio-test"   % Versions.zioSchema % Test,
      ),
    )
    .settings(
      mimaBinaryIssueFilters ++= Seq(
        ProblemFilters.exclude[Problem]("zio.schema.codec.circe.internal.*"),
        // internal api changes
        ProblemFilters.exclude[MissingClassProblem]("zio.schema.codec.circe.package"),
        ProblemFilters.exclude[MissingClassProblem]("zio.schema.codec.circe.package$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.schema.codec.circe.package.folder"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.schema.codec.circe.package.fromJson"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.schema.codec.circe.package.toJson"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.schema.codec.circe.package.toJsonNumber"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.schema.codec.circe.package.toJsonObject"),
        // replaced with (implicit config: Configuration = Configuration.default) implicit parameter
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.schema.codec.circe.CirceCodec.schemaDecoder"),
      ),
    )
    .settings(macroDefinitionSettings)
    .settings(crossProjectSettings)
    .settings(Test / fork := crossProjectPlatform.value == JVMPlatform)
    .jsSettings(
      scalaJSLinkerConfig ~= { _.withOptimizer(false) },
      scalaJSUseMainModuleInitializer := true,
    )
    .dependsOn(core)

lazy val zioSchemaCirceJsoniter =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .in(file("zio-schema-circe-jsoniter"))
    .enablePlugins(BuildInfoPlugin)
    .settings(stdSettings("zio-schema-circe-jsoniter"))
    .settings(buildInfoSettings("zio.schema.codec.circe.jsoniter"))
    .settings(dottySettings)
    .settings(
      libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-circe" % Versions.jsoniter,
    )
    .settings(
      mimaBinaryIssueFilters ++= Seq(
        ProblemFilters.exclude[Problem]("zio.schema.codec.circe.jsoniter.internal.*"),
        // deprecation of jsoniter based Schema[Json]
        ProblemFilters.exclude[MissingClassProblem]("zio.schema.codec.circe.jsoniter.package"),
        ProblemFilters.exclude[MissingClassProblem]("zio.schema.codec.circe.jsoniter.package$"),
        // caused by adding CirceJsoniterCodec.Config and CirceJsoniterCodec.Configuration
        ProblemFilters.exclude[IncompatibleResultTypeProblem](
          "zio.schema.codec.circe.jsoniter.CirceJsoniterCodec.schemaEncoder$default$2",
        ),
        ProblemFilters.exclude[IncompatibleMethTypeProblem](
          "zio.schema.codec.circe.jsoniter.CirceJsoniterCodec.schemaEncoder",
        ),
        ProblemFilters.exclude[IncompatibleMethTypeProblem](
          "zio.schema.codec.circe.jsoniter.CirceJsoniterCodec.schemaBasedBinaryCodec",
        ),
        ProblemFilters.exclude[IncompatibleMethTypeProblem](
          "zio.schema.codec.circe.jsoniter.CirceJsoniterCodec#CirceJsoniterEncoder.encode",
        ),
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "zio.schema.codec.circe.jsoniter.CirceJsoniterCodec.schemaDecoder",
        ),
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "zio.schema.codec.circe.jsoniter.CirceJsoniterCodec.schemaCodec",
        ),
      ),
    )
    .settings(macroDefinitionSettings)
    .settings(crossProjectSettings)
    .settings(Test / fork := crossProjectPlatform.value == JVMPlatform)
    .jsSettings(
      scalaJSLinkerConfig ~= { _.withOptimizer(false) },
      scalaJSUseMainModuleInitializer := true,
    )
    .dependsOn(core, zioSchemaCirce % "test->test")
