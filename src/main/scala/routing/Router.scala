package router

import router.util._
import router.gtfs._

import javax.ws.rs.core._
import javax.ws.rs._

import geotrellis.raster.op._
import geotrellis.rest.op._
import geotrellis.feature._
import geotrellis._

import com.vividsolutions.jts.geom.{Coordinate}

object Context {
  val shapes:Seq[LineString[Int]] =
    parseShapesFromFile("gtfs/shapes.txt").right.getOrElse(
      sys.error("Could not load file"))

  val phillyExtent = Extent(-8383693, 4845038, -8341837, 4884851)
  val cellWidthTgt = 30.0 // meters
  val cellHeightTgt = cellWidthTgt

  // We want to have an integer number for rows
  // and cols so we recalc cell width and height
  val rows = ((phillyExtent.ymax - phillyExtent.ymin) / cellHeightTgt).toInt
  val cellWidth = (phillyExtent.ymax - phillyExtent.ymin) / rows

  val cols = ((phillyExtent.xmax - phillyExtent.xmin) / cellWidthTgt).toInt
  val cellHeight = (phillyExtent.xmax - phillyExtent.xmin) / cols

  val phillyRasterExtent =
    RasterExtent(phillyExtent, cellWidth, cellHeight, cols, rows)

  val catalog = process.Catalog("", Map.empty, "", "")
  val server = process.Server("demo", catalog)
}

@Path("/test")
class TestResource {

  @GET
  def doit = {


    Response.ok("<h1>Hello World</h1>").`type`("text/html").build()
  }
}

@Path("/hello")
class HelloResource {

  @GET
  def hello =
    Response.ok("<h1>Hello World</h1>").`type`("text/html").build()
}
