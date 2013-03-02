package router

import router.util._

import geotrellis.feature._
import com.vividsolutions.jts.geom.{Coordinate}

object gtfs {

  case class Shape(shapeId: Int, shapeOrder: Int, lat: Double, lng: Double)

  implicit val shapeOrder:math.Ordering[Shape] = new math.Ordering[Shape] {
    def compare(x: Shape, y: Shape) =
      implicitly[Ordering[Int]].compare(x.shapeOrder, y.shapeOrder)
  }

  def parseShapeDefs(lines: Seq[String]):Either[Seq[String], Seq[Shape]] =
    sequence(lines.map { part =>
      part split(",") match {
        case Array(I(shapeId), D(lat), D(lng), I(order)) =>
          Right(Shape(shapeId, order, lat, lng))
        case _ =>
          Left(s"Could not parse line $part")
      }
    })

  def shapeSeqToFt(s: Seq[Shape], d: Int):LineString[Int] =
    LineString(
      epsg4326factory.createLineString(
        s.map(shape => new Coordinate(shape.lng, shape.lat)).toArray),
      d)

  def parseShapesAsFeatures(shapes: Seq[Shape]):Seq[LineString[Int]] =
    shapes
      .groupBy(_.shapeId)
      .mapValues(_.sorted)
      .toSeq
      .map(s => shapeSeqToFt(s._2, s._1))
      .sorted

  def parseShapesFromStrings(s: Seq[String]):Either[Seq[String], Seq[LineString[Int]]] =
    parseShapeDefs(s.map(_.trim))
      .right
      .map(s => parseShapesAsFeatures(s))

  def parseShapesFromString(s: String):Either[Seq[String], Seq[LineString[Int]]] =
    parseShapesFromStrings(s.split("\n"))

  def trimFirstLine(s: String) =
    s.substring(s.indexOf("\n")+1)

  /*
   * trip_id,arrival_time,departure_time,stop_id,stop_sequence
   * 3509653,05:14:00,05:14:00,29782,32
   */
  /*
   * stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station,zone_id,wheelchair_boarding                                                                              * 2,Ridge Av & Lincoln Dr  ,40.014986,-75.206826,,31032,1,0
   */

  def parseStops(s: Seq[String]):Either[Seq[String], Map[Int, (Double, Double)]] =
    sequence(
      s.map(_.split(",")).map(_ match {
        case Array(I(stop_id), _, D(lat), D(lng), _, _, _, _) =>
          right(stop_id -> (lng, lat))
        case v => left(s"Could not parse $v")
      })).right.map(Map(_:_*))

  // Time of day in decimal (10:15 -> 10.25, 13:30 -> 13.5)
  def parseTrips(s: Seq[String]):Either[Seq[String], Map[Int, Seq[(Double, Int)]]] =
    sequence(
      s.map(_.split(",")).map(_ match {
        case Array(I(tid), T(time), _, I(stop_id), I(stop_seq)) =>
          right((tid, time, stop_id, stop_seq))
        case v => left(sys.error(s"Couldn't parse ${v.toSeq}"))
      })).right
      .map(_.groupBy(_._1)
        .mapValues(_.sortWith(_._4 < _._4)
          .map(t => (t._2, t._3))))


  def fileParser[L,R](f: Seq[String] => Either[L, R]) =
    (s:String) => f(trimFirstLine(
      io.Source
        .fromFile(s)
        .mkString)
      .split("\n"))

  def parseStopFromFile(s: String):Either[Seq[String], Map[Int, (Double, Double)]] =
    fileParser(parseStops)(s)

  def parseShapesFromFile(s: String):Either[Seq[String], Seq[LineString[Int]]] =
    fileParser(parseShapesFromStrings)(s)

  def parseTripsFromFile(s: String) =
    fileParser(parseTrips)(s)

}
