package eu.semberal.dbstress.util

import breeze.linalg.DenseVector

object ModelExtensions {
  private val MissingString: String = "-"

  implicit class StatisticalExtensions(l: List[Double]) {

    private lazy val v = DenseVector(l: _*)

    def median: Option[Double] = withNonEmptyList(breeze.stats.median apply _)
    def mean: Option[Double] = withNonEmptyList(breeze.stats.mean apply _)
    def variance: Option[Double] = withNonEmptyList(breeze.stats.variance apply _)

    def minimum: Option[Double] = withNonEmptyList(breeze.linalg.min apply _)
    def maximum: Option[Double] = withNonEmptyList(breeze.linalg.max apply _)

    private def withNonEmptyList(f: DenseVector[Double] => Double) = if(l.isEmpty) None else Some(f(v))
  }

  implicit class OptionExtension[T](o: Option[T]) {
    def getOrMissingString: String = o.map(_.toString).getOrElse(MissingString)
  }
}