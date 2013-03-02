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

  def parseShapesFromString(s: String):Either[Seq[String], Seq[LineString[Int]]] =
    parseShapeDefs(s.split("\n").map(_.trim()))
      .right
      .map(s => parseShapesAsFeatures(s))

  def trimFirstLine(s: String) =
    s.substring(s.indexOf("\n")+1)

  def parseShapesFromFile(s: String):Either[Seq[String], Seq[LineString[Int]]] =
    parseShapesFromString(
      trimFirstLine(
        io.Source
          .fromFile(s)
          .mkString))

}
