package scalaxy.json.base

import org.json4s._
import org.json4s.jackson.JsonMethods
import com.fasterxml.jackson.core.JsonParser.Feature._
import com.fasterxml.jackson.databind.ObjectMapper

abstract class ExtractibleJSONStringContext(context: StringContext) {

  def parse(str: String): JValue

  import ExtractibleJSONStringContext._

  // No extractor macros yet: https://issues.scala-lang.org/browse/SI-5903
  // Extractor macros work in 2.11.0-SNAPSHOT: https://github.com/paulp/scala/pull/9
  // Also see "extractors become name-based": https://github.com/scala/scala/pull/2848
  def unapplySeq(str: String): Option[Seq[Any]] =
    unapplySeq(parse(str))

  def unapplySeq(value: JValue): Option[Seq[Any]] =
    extract(value, strict = true)

  private def extract(value: JValue, strict: Boolean): Option[Seq[Any]] = {
    val Placeholders(placeholders, placeholderNames, posMap, _) =
      preparePlaceholders[Unit](context.parts.map(_ -> {}), _ => false, _ => {})

    val results = new Array[Any](context.parts.size - 1)
    val replacements = placeholderNames.zipWithIndex.toMap
    def visit(placeholders: JValue, v: JValue): Boolean = (placeholders, v) match {
      case (JNull, JNull) =>
        true
      case (JNothing, JNothing) =>
        true
      case (JObject(pvalues), JObject(values)) =>
        val pmap = pvalues.toMap
        val map = values.toMap
        (pmap.keys == map.keys) &&
          pmap.toSeq.forall({
            case (pkey, pvalue) =>
              if (replacements.contains(pkey))
                sys.error("Name patterns not supported by json extractor.")
              map.get(pkey).exists(value => {
                visit(pvalue, value)
              })
          })
      case (JArray(pvalues), JArray(values)) =>
        pvalues.zip(values).forall({
          case (pvalue, value) =>
            visit(pvalue, value)
        })
      case (JString(pv), v) if replacements.contains(pv) =>
        results(replacements(pv)) = v // TODO peel
        true
      case (JString(pv), JString(v)) if pv == v =>
        true
      case (JBool(pv), JBool(v)) if pv == v =>
        true
      case (JDouble(pv), JDouble(v)) if pv == v =>
        true
      case (JInt(pv), JInt(v)) if pv == v =>
        true
      case (JDecimal(pv), JDecimal(v)) if pv == v =>
        true
      case _ =>
        false
    }
    if (visit(parse(placeholders), value)) {
      Some(results)
    } else {
      None
    }
  }
}

case class Placeholders[P](
  sourceWithPlaceholders: String,
  placeholderNames: List[String],
  posMap: Map[Int, P],
  dotsName: String)

object ExtractibleJSONStringContext {
  def preparePlaceholders[P](parts: Seq[(String, P)], paramIsField: Int => Boolean, getParamPos: Int => P): Placeholders[P] = {
    val radix = {
      val concat = parts.mkString("")
      var i = 0
      var r: String = null
      while ({ r = if (i == 0) "_" else "_" + i; concat.contains(r) }) {
        i += 1
      }
      r
    }

    val params = (0 until parts.size - 1).map(radix + _).toList
    val dotsName = radix + "d"
    val b = new StringBuilder
    var posMap = scala.collection.immutable.TreeMap[Int, P]()
    var i = 0 // faster than zipWithIndex
    for (((part, partPos), param) <- parts.zip(params)) {
      posMap += (b.size -> partPos)
      b ++= part
      posMap += (b.size -> getParamPos(i))
      b += '"'
      b ++= param
      b += '"'
      if (paramIsField(i)) {
        b ++= ":0"
      }
      i += 1
    }
    val lastPart = parts.last
    posMap += (b.size -> lastPart._2)
    b ++= lastPart._1

    Placeholders[P](
      sourceWithPlaceholders = b.toString,
      placeholderNames = params,
      posMap = posMap,
      dotsName = dotsName)
  }
}
