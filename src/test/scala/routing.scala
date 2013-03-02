package router

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite

import router.util._

import geotrellis.feature._
import com.vividsolutions.jts.geom.{Coordinate}


class GeomParser extends FunSuite with ShouldMatchers {
  test("parse shape.txt format correctly") {
    val shapes1 = """1,39.952096,-75.16145,1
                     1,39.952687,-75.161331,2
                     1,39.952755,-75.16132,3
                     2,39.952886,-75.161289,4
                     2,39.953128,-75.161236,5
                     3,39.953537,-75.161144,6
                     3,39.953618,-75.161126,7"""

    val shapes2 = """2,39.952886,-75.161289,4
                     1,39.952096,-75.16145,1
                     1,39.952755,-75.16132,3
                     3,39.953618,-75.161126,7
                     1,39.952687,-75.161331,2
                     3,39.953537,-75.161144,6
                     2,39.953128,-75.161236,5"""


    val exp = Seq(
      LineString(
        epsg4326factory.createLineString(
          Array(new Coordinate(-75.16145, 39.952096),
            new Coordinate(-75.161331, 39.952687),
            new Coordinate(-75.16132, 39.952755))), 1),
      LineString(
        epsg4326factory.createLineString(
          Array(new Coordinate(-75.161289, 39.952886),
            new Coordinate(-75.161236, 39.953128))), 2),
      LineString(
        epsg4326factory.createLineString(
          Array(new Coordinate(-75.161144, 39.953537),
            new Coordinate(-75.161126, 39.953618))), 3))


    gtfs.parseShapesFromString(shapes1) should equal(right(exp))
    gtfs.parseShapesFromString(shapes2) should equal(right(exp))


  }
}
