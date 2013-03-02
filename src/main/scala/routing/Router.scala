package router

import router.util._
import router.gtfs._

import javax.ws.rs.core._
import javax.ws.rs._

import geotrellis.raster.op._
import geotrellis.rest.op._
import geotrellis.feature.LineString
import geotrellis._

import com.vividsolutions.jts.geom.{Coordinate}

object Context {
  val walkingSpeedMph = 3.5
  val busSpeedMph = 11.0

  val walkingSpeedMetersPerSec = walkingSpeedMph * 0.44704
  val busSpeedMetersPerSec = busSpeedMph * 0.44704

  val phillyExtent = Extent(-8383693, 4845038, -8341837, 4884851)
  val cellWidthTgt = 30.0 // meters
  val cellHeightTgt = cellWidthTgt

  // We want to have an integer number for rows
  // and cols so we recalc cell width and height
  val rows = ((phillyExtent.ymax - phillyExtent.ymin) / cellHeightTgt).toInt
  val cellWidth = (phillyExtent.ymax - phillyExtent.ymin) / rows

  val cols = ((phillyExtent.xmax - phillyExtent.xmin) / cellWidthTgt).toInt
  val cellHeight = (phillyExtent.xmax - phillyExtent.xmin) / cols

  // Truncate to integers in seconds
  val walkingTimeForCell = (cellWidth / walkingSpeedMetersPerSec).toInt
  val busTimeForCell = (cellWidth / busSpeedMetersPerSec).toInt

  val phillyRasterExtent =
    RasterExtent(phillyExtent, cellWidth, cellHeight, cols, rows)

  val catalog = process.Catalog("", Map.empty, "", "")
  val server = process.Server("demo", catalog)

  println("Parsing trips...")
  val trips = parseTripsFromFile("gtfs/stop_times.txt").right.get.values

  println("Parsing stops...")
  val stops = parseStopFromFile("gtfs/stops.txt").right.get

  println("Creating line strings...")
  val tripsInRange = trips.take(100) // First 10 trips of the day
  val lines:Seq[LineString[Int]] =
    tripsInRange.toSeq.map { tripStops =>
      LineString(
        epsg4326factory.createLineString(
          tripStops.map(s => stops.get(s._2) match {
            case Some((lng,lat)) => {
              val m = latLonToMeters(lat, lng)
              new Coordinate(m._1,m._2)
            }
            case None => sys.error(s"bad stop $s")
          }).toArray), 1)
    }

  println("Rasterizing %s lines..." format lines.length)
  val busTimeRaster =
    server.run(
      RasterizeLines(
        phillyRasterExtent,
        busTimeForCell,
        lines))

  println("Server ready to go")

}

@Path("/test")
class TestResource {

  @GET
  def transit(
    @QueryParam("bbox")
    bbox: String,

    @QueryParam("width")
    cols: String,

    @QueryParam("height")
    rows: String) = {

    // Create a blank raster
    val raster = CreateRaster(Context.phillyRasterExtent)

    // Assign a 'friction value' of walking to all of the cells
    val rasterWalks = local.DoCell(raster, _ => Context.walkingTimeForCell)

    // Rasterize the bus lines. Use the bus speed for the friction
    // value (this should be much lower than walking)
    val rasterBoth = AMin(rasterWalks, Context.busTimeRaster)

    val costs = focal.CostDistance(rasterBoth, Seq((50,50)))

    val pngOp = io.SimpleRenderPng(costs)
    val png = Context.server.run(pngOp)

    Response.ok(png).`type`("image/png").build()
  }
}
