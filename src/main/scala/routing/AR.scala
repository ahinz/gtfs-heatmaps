package router

import com.vividsolutions.jts.geom.{Coordinate}

import geotrellis.feature.rasterize._
import geotrellis.feature._
import geotrellis._

object AR {
  def foreachCellByLineString[D](p:LineString[D], re:RasterExtent)(f: Callback[LineString,D]) {
    val geom = p.geom
    val coords:Array[Coordinate] = geom.getCoordinates()

    var i = 0
    val l = coords.length - 1
    while(i < l) {
      val p0 = coords(i)
      val p1 = coords(i+1)

      val p0x = re.mapXToGrid(p0.x)
      val p0y = re.mapYToGrid(p0.y)
      val p1x = re.mapXToGrid(p1.x)
      val p1y = re.mapYToGrid(p1.y)

      //println("Rasterizing %s and %s -> %s, %s %s, %s" format (p0, p1, p0x, p0y, p1x, p1y))

      if (p0x < p1x) {
        foreachCellInGridLine(p0x, p0y, p1x, p1y, p, re)(f)
      } else {
        foreachCellInGridLine(p1x, p1y, p0x, p0y, p, re)(f)
      }

      i += 1
    }
  }

  //TODO: optimizations, including getting line within raster extent
  //test for horizontal and vertical lines
  private def foreachCellInGridLine[D](x0:Int, y0:Int, x1:Int, y1:Int, p:LineString[D], re:RasterExtent)(f:Callback[LineString,D]) {
    val dy = y1 - y0
    val dx = x1 - x0
    val m:Double = dy.toDouble / dx.toDouble

    // if a step in x creates a step in y that is greater than one, reverse
    // roles of x and y.
    if (math.abs(m) > 1) {
      foreachCellInGridLineSteep[D](x0, y0, x1, y1, p, re)(f)
    }

    var x:Int = x0
    var y:Double = y0

    val ymax = re.extent.ymax
    val ymin = re.extent.ymin
    val xmax = re.extent.xmax
    val xmin = re.extent.xmin

    val cols = re.cols
    val rows = re.rows

    while(x <= x1) {
      //println("x: %d, y: %f".format(x,y))

      val newY:Int = (y + 0.5).asInstanceOf[Int]
      if (x >= 0 && y >= 0 && x < cols && y < rows) {
        f(x, newY, p)
      }
      y += m
      x = x + 1
    }
  }

  private def foreachCellInGridLineSteep[D](x0:Int, y0:Int, x1:Int, y1:Int, p:LineString[D], re:RasterExtent)(f: Callback[LineString,D]) {
    val dy = y1 - y0
    val dx = x1 - x0
    val m = dy.toDouble / dx.toDouble

    // if a step in x creates a step in y that is less than one, reverse
    // roles of x and y.
    if (math.abs(m) < 1) {
      foreachCellInGridLine[D](x0, y0, x1, y1, p, re)(f)
    }

    var x:Int = x0
    var y:Int = y0

    val ymax = re.extent.ymax
    val ymin = re.extent.ymin
    val xmax = re.extent.xmax
    val xmin = re.extent.xmin

    val cols = re.cols
    val rows = re.rows

    val step = (1/m).toInt
    while(y <= y1) {
      //println("x: %f, y: %f".format(x,y))
      val newX:Int = (x + 0.5).asInstanceOf[Int]
      if (x >= 0 && y >= 0 && x < cols && y < rows) {
        f(newX, y, p)
      }
      x += step
      y = y + 1
    }
  }
}
