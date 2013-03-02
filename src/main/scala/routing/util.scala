package router

import geotrellis.feature.LineString

import com.vividsolutions.jts.geom.{GeometryFactory, PrecisionModel}

object util {
  implicit val linestringIntIsOrdered = new Ordering[LineString[Int]] {
    def compare(a: LineString[Int], b: LineString[Int]) =
      implicitly[Ordering[Int]].compare(a.data, b.data)
  }

  class SafeStringOp[T](f: String => T) {
    def unapply(s: String):Option[T] =
      try { Some(f(s)) } catch { case _:Exception => None }
  }

  val epsg4326factory = new GeometryFactory(new PrecisionModel(), 4326)

  object D extends SafeStringOp(_.toDouble)
  object I extends SafeStringOp(_.toInt)

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


}
