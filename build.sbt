name := "media-lib"

organization := "eu.delving"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.0"

description := "media manipulation utilities"

licenses := Seq("Apache License Version 2" -> url("https://github.com/sbt/sbt-buildinfo/blob/master/LICENSE"))

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.10"

publishMavenStyle := true

publishTo <<= (version) { version: String =>
   val delvingRepository = "http://nexus.delving.org/nexus/content/repositories/"
   val (name, u) = if (version.contains("-SNAPSHOT")) ("Delving Snapshot Repository", delvingRepository+"snapshots")
                   else ("Delving Releases Repository", delvingRepository+"releases")
   Some(Resolver.url(name, url(u))(Resolver.ivyStylePatterns))
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
