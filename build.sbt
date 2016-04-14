//javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }


lazy val root = (project in file(".")).
  settings(
    name := "fastly-sqs",
    version := "1.2.0",
    scalaVersion := "2.11.7",
    retrieveManaged := true,
    libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.10.50",
    libraryDependencies += "com.amazonaws" % "aws-java-sdk-logs" % "1.10.50",
    libraryDependencies += "com.amazonaws" % "aws-java-sdk-sqs" % "1.10.50",
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.4",
    libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.3",
    libraryDependencies += "org.json4s" %% "json4s-native" % "3.3.0",
    libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.3.0",
    libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.4.4",
    libraryDependencies += "com.typesafe" % "config" % "1.3.0",
    libraryDependencies += "org.specs2" %% "specs2-core" % "3.7" % "test"
  )


fork  := true

javaOptions in Test += "-Dconfig.resource=application.test.conf"

javaOptions in run ++= Seq("-Xms3G","-Xmx3G","-Dconfig.resource=application.conf")

assemblyMergeStrategy in assembly  :=
   {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
   }

