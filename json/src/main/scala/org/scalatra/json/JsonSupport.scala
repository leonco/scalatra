package org.scalatra
package json

import java.io.{InputStreamReader, InputStream}
import org.json4s._
import Xml._
import text.Document

object JsonSupport {

  val ParsedBodyKey = "org.scalatra.json.ParsedBody"
}

trait JsonSupport[T] extends JsonOutput[T] {

  import JsonSupport._

  protected def parseRequestBody(format: String) = try {
    if (format == "json") {
      transformRequestBody(readJsonFromStream(request.inputStream))
    } else if (format == "xml") {
      transformRequestBody(readXmlFromStream(request.inputStream))
    } else JNothing
  } catch {
    case _: Throwable ⇒ JNothing
  }

  protected def readJsonFromStream(stream: InputStream): JValue
  protected def readXmlFromStream(stream: InputStream): JValue = {
    val JObject(JField(_, jv) :: Nil) = toJson(scala.xml.XML.load(stream))
    jv
  }
  protected def transformRequestBody(body: JValue) = body

  override protected def invoke(matchedRoute: MatchedRoute) = {
    withRouteMultiParams(Some(matchedRoute)) {
      val mt = request.contentType map {
        _.split(";").head
      } getOrElse "application/x-www-form-urlencoded"
      val fmt = mimeTypes get mt getOrElse "html"
      if (shouldParseBody(fmt)) {
        request(ParsedBodyKey) = parseRequestBody(fmt).asInstanceOf[AnyRef]
      }
      super.invoke(matchedRoute)
    }
  }

  private def shouldParseBody(fmt: String) =
    (fmt == "json" || fmt == "xml") && parsedBody == JNothing

  def parsedBody: JValue = request.get(ParsedBodyKey).map(_.asInstanceOf[JValue]) getOrElse {
    val fmt = format
    var bd: JValue = JNothing
    if (fmt == "json" || fmt == "xml") {
      bd = parseRequestBody(fmt)
      request(ParsedBodyKey) = bd.asInstanceOf[AnyRef]
    }
    bd
  }
}
