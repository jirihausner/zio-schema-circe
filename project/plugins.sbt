addSbtPlugin("com.eed3si9n"       % "sbt-assembly"                  % "2.3.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"                  % "0.13.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.5.2")
addSbtPlugin("com.github.sbt"     % "sbt-native-packager"           % "1.10.4")
addSbtPlugin("com.typesafe"       % "sbt-mima-plugin"               % "1.1.4")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"                 % "0.13.1")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.18.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.6")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"                % "1.9.2")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.8"
