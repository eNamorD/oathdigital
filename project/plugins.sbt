addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
// The jsdom test environment for the frontend project (build.sbt sets
// `Test / jsEnv`). Not a plugin: it is a build-classpath library the
// build file instantiates directly.
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
