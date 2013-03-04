package router

import router.util._
import router.gtfs._

import javax.ws.rs.core._
import javax.ws.rs._

import geotrellis.raster.op._
import geotrellis.rest.op._
import geotrellis.feature.LineString
import geotrellis._
import geotrellis.data.arg.{ArgWriter, ArgReader}

import com.vividsolutions.jts.geom.{Coordinate}

object Context {
  val walkingSpeedMph = 3.5
  val walkingSpeedMetersPerSec = walkingSpeedMph * 0.44704

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

  val phillyRasterExtent =
    RasterExtent(phillyExtent, cellWidth, cellHeight, cols, rows)

  val catalog = process.Catalog("", Map.empty, "", "")
  val server = process.Server("demo", catalog)

  // Create a blank raster
  val raster = CreateRaster(phillyRasterExtent)

  // Assign a 'friction value' of walking to all of the cells
  val rasterWalks = local.DoCell(raster, _ => walkingTimeForCell)

  val cachePath = "/tmp/cached_transit_raster.arg"
  val f = new java.io.File(cachePath)
  lazy val transitTimeRaster =
    if (f.exists) {
      println("Found cached raster (%s)..." format cachePath)

      ArgReader.readPath(cachePath, None, None)
    } else {
      println("Parsing trips...")
      val trips = parseTripsFromFile("gtfs/stop_times.txt").right.get.values

      println(s"Parsed ${trips.toSeq.length} trips!")
      println("Parsing stops...")
      val stops = parseStopFromFile("gtfs/stops.txt").right.get

      println("Creating line strings...")
      // Morning range:
      // 6h to 10h
      val startTime = 6.0 * 60.0 * 60.0
      val endTime = 10.0 * 60.0 * 60.0

      val tripsInRange = trips.filter { t =>
        t.foldLeft(false)((b, x) =>
          b || (x._1 >= startTime && x._1 <= endTime))
      }

      def stopsToCoords(trip: Seq[(Double, Int)]) =
        trip.map(s => stops.get(s._2) match {
          case Some((lng,lat)) => {
            val m = latLonToMeters(lat, lng)
            new Coordinate(m._1,m._2)
          }
          case None => sys.error(s"bad stop $s")
        })

      def stopsToTimeInterval(trip: Seq[(Double, Int)]) =  {
        val t = trip.map(_._1)
        t.max - t.min
      }

      def dist(s: Seq[Coordinate]) =
        s.zip(s.tail).map(a => a._1.distance(a._2)).reduceLeft(_+_)

      def stopsToLineString(trip: Seq[(Double, Int)]) = {
        val coords = stopsToCoords(trip)
        val distance = dist(coords)
        val time = stopsToTimeInterval(trip)
        val speed = distance / time               // meters per second
        val cellTime = (cellWidth / speed).toInt  // seconds

        LineString(
          epsg3857factory.createLineString(
            coords.toArray),
          cellTime)
      }

      val lines:Seq[LineString[Int]] =
        tripsInRange.toSeq.map(tripStops => stopsToLineString(tripStops))

      val cb = new Updater {
        def apply(c: Int, r: Int, g: LineString[Int], data: IntArrayRasterData) {
          val prev = data.get(c,r)
          data.set(c, r,
            if (prev == NODATA) {
              g.data
            } else {
              math.min(g.data, prev)
            })
        }
      }

      println("Rasterizing %s lines..." format lines.length)
      val busTimeRaster =
        server.run(
          RasterizeLines(
            phillyRasterExtent,
            cb,
            lines))

      println("Rendering transit template")

      // Rasterize the bus lines. Use the bus speed for the friction
      // value (this should be much lower than walking)
      val rasterBothOp = AMin(rasterWalks, busTimeRaster)

      val renderedRaster = server.run(rasterBothOp)

      println("Writing raster to disk (%s)..." format cachePath)
      ArgWriter(TypeInt).write(cachePath, renderedRaster, "transit")
      renderedRaster
    }

  println("Server ready to go")

}

@Path("/test")
class TestResource {

  @GET
  def transit(
    @QueryParam("lat")
    latstr: String,

    @QueryParam("lng")
    lngstr: String) = {

    val lato = D(latstr)
    val lngo = D(lngstr)

    val ll =
      for(lat <- lato;
        lng <- lngo)
      yield latLonToMeters(lat, lng)

    val (x,y) = Context
      .phillyRasterExtent
      .mapToGrid(ll.getOrElse((-8367552.0, 4855775.0)))

    println(s"X->$x Y->$y")

    val costsOp = focal.CostDistance(Context.transitTimeRaster, Seq((x,y)))
    //val costsOp = focal.CostDistance(Context.rasterWalks, Seq((x,y)))

    val ramp:Seq[Int] = geotrellis.data.ColorRamps.HeatmapBlueToYellowToRedSpectrum.colors :+ 0x0
    val breaks:Seq[Int] =
      (1 to ramp.length).map(_*5*60) // 10 minute intervals

    val costs = Context.server.run(costsOp)
    val hist = Context.server.run(statistics.op.stat.GetHistogram(costs, 100000))

    val pngOp = io.RenderPng(costs,
      geotrellis.data.ColorBreaks(breaks.toArray, ramp.toArray),
      hist,
      0x0)

    val png = Context.server.run(pngOp)

    Response.ok(png).`type`("image/png").build()
  }
}
