//javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

resolvers += Resolver.jcenterRepo

val awsVersion = "1.10.50"

def awsSdkModule(id: String) = "com.amazonaws" % s"aws-java-sdk-$id" % awsVersion

lazy val root = (project in file(".")).
  settings(
    name := "fastly-sqs",
    version := "2.0.1",
    scalaVersion := "2.11.7",
    retrieveManaged := true,
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.4",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.3",
      "org.json4s" %% "json4s-native" % "3.3.0",
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "com.typesafe.play" %% "play-ws" % "2.4.4",
      "com.typesafe" % "config" % "1.3.0",
      "org.specs2" %% "specs2-core" % "3.7" % "test",
      "com.iheart" %% "ficus" % "1.2.3"
    ) ++ Seq("s3", "logs", "sqs").map(awsSdkModule)
  )

fork  := true

javaOptions in Test += "-Dconfig.resource=application.test.conf"

javaOptions in run ++= Seq("-Xms3G","-Xmx3G","-Dconfig.resource=application.conf")

assemblyMergeStrategy in assembly  := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
 }

