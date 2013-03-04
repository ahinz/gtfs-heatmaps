package router

import geotrellis.feature.LineString
import geotrellis.feature.rasterize._
import geotrellis._

import com.vividsolutions.jts.geom.{GeometryFactory, PrecisionModel}

object util {
  implicit val linestringIntIsOrdered = new Ordering[LineString[Int]] {
    def compare(a: LineString[Int], b: LineString[Int]) =
      implicitly[Ordering[Int]].compare(a.data, b.data)
  }

  class SafeStringOp[T](f: String => T) {
    def unapply(s: String):Option[T] =
      try { Some(f(s.trim())) } catch { case _:Exception =>
          { println("--- failed to parse: '%s' ---".format(s))
            None }
      }

    def apply(s: String):Option[T] = unapply(s)
  }

  val epsg3857factory = new GeometryFactory(new PrecisionModel(), 3857)

  def timeStrToDouble(t: String) =
    t.split(":") match {
      case Array(I(hr), I(min)) => hr * 60.0 * 60.0 + min * 60.0
      case Array(I(hr), I(min), I(sec)) =>
        hr * 60.0 * 60.0 + min * 60.0 + sec.toDouble
      case s => {
        println(s"Invalid time format $t (${s.toSeq})")
        sys.error(s"Invalid time format $t")
      }
    }

  object D extends SafeStringOp(_.toDouble)
  object I extends SafeStringOp(_.toInt)
  object T extends SafeStringOp(timeStrToDouble)

  def right[A,B](b: B):Either[A,B] = Right[A,B](b)
  def left[A,B](a: A):Either[A,B] = Left[A,B](a)

  // This version is pretty much too slow to do anything...
  // def sequence[A,B](s: Seq[Either[A,B]]):Either[Seq[A], Seq[B]] =
  //   s.foldLeft(right[Seq[A], Seq[B]](Seq.empty)) { (sum, e) =>
  //     sum.fold(
  //       lefts => e.fold(
  //         l => left(lefts :+ l),
  //         _ => left(lefts)),
  //       rights => e.fold(
  //         l => left(Seq(l)),
  //         r => right(rights :+ r)))
  //   }

  // This is gross as hell but 100x as fast as the pretty version
  def sequence[A,B](s: Seq[Either[A,B]]):Either[Seq[A], Seq[B]] =
    try {
      right(s.map(_.right.get))
    } catch {
      case _: Exception =>
        left(s.filter(_.isLeft).map(_.left.get))
    }

  val originShift = 2 * math.Pi * 6378137 / 2.0

  def latLonToMeters(lat:Double, lon:Double) = {
    val mx = lon * originShift / 180.0
    val my1:Double = ( math.log( math.tan((90 + lat) * math.Pi / 360.0 )) / (math.Pi / 180.0) )
    val my = my1 * originShift / 180

    (mx, my)
  }

  case class AMin(r1: Op[Raster], r2: Op[Raster]) extends
      Op2[Raster,Raster,Raster](r1, r2)({ (r1, r2) =>
        Result(
          r1.combine(r2) { (z1,z2) =>
            if (z1 == NODATA) z2
            else if (z2 == NODATA) z1
            else math.min(z1,z2)
          })
      })

  type RE = RasterExtent
  type S = Seq[LineString[Int]]

  trait Updater {
    def apply(c: Int, r: Int, g: LineString[Int], d: IntArrayRasterData)
  }

  case class RasterizeLines(r: Op[RE], up: Op[Updater], l: Op[S])
      extends Op3[RE, Updater, S, Raster](r,up,l)({ (r,up,l) =>
    val data = IntArrayRasterData.empty(r.cols, r.rows)

    val cb = new Callback[LineString, Int] {
      def apply(c: Int, r: Int, g: LineString[Int]) {
        up(c,r,g,data)
      }
    }

    for(ft <- l) {
      try {
        AR.foreachCellByLineString(ft, r)(cb)
      } catch {
        case e:Exception => {
          println(s"Failed to rasterize a line ($ft)")
          e.printStackTrace
        }
      }
    }

    Result(new Raster(data, r))
      })

}
