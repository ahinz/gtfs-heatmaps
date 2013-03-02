name := "Geotrellis Routing Demo"

scalaVersion := "2.10.0"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-optimize")

parallelExecution := false

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis" % "0.8.0",
  "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)

resolvers ++= Seq(Resolver.sonatypeRepo("releases"),
                  Resolver.sonatypeRepo("snapshots"),
                  "Geotools" at "http://download.osgeo.org/webdav/geotools/")

mainClass in (Compile, run) := Some("geotrellis.rest.WebRunner")
