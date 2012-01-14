package org.scalatra
package tests

import java.util.Locale
import Locale.ENGLISH
import com.ning.http.client._
import java.nio.charset.Charset
import java.io.InputStream
import scalax.io.{Codec, Resource}
import collection.JavaConversions._
import java.net.URI
import rl.{MapQueryString, QueryString}
import java.util.concurrent.TimeUnit

object StringHttpMethod {
  val GET = "GET"
  val POST = "POST"
  val DELETE = "DELETE"
  val PUT = "PUT"
  val CONNECT = "CONNECT"
  val HEAD = "HEAD"
  val OPTIONS = "OPTIONS"  
  val PATCH = "PATCH"  
  val TRACE = "TRACE"
}

abstract class ClientResponse {
  
  def status: ResponseStatus
  def contentType: String  
  def charset: Charset 
  def inputStream: InputStream
  def cookies: Map[String, org.scalatra.Cookie]
  def headers: Map[String, String] 
  def uri: URI
  
  private var _body: String = null
  
  def statusCode = status.code
  def statusText = status.line
  def body = {
    if (_body == null) _body = Resource.fromInputStream(inputStream).slurpString(Codec(charset))
    _body
  }
} 

class NettyClient(val host: String, val port: Int) extends Client {

  import StringHttpMethod._
  private val clientConfig = new AsyncHttpClientConfig.Builder().setFollowRedirects(false).build()
  private val underlying = new AsyncHttpClient(clientConfig) {
    def preparePatch(uri: String): AsyncHttpClient#BoundRequestBuilder = requestBuilder("PATCH", uri)
    def prepareTrace(uri: String): AsyncHttpClient#BoundRequestBuilder = requestBuilder("TRACE", uri)
  }

  override def stop() {
    underlying.close()
  }

  private def requestFactory(method: String): String ⇒ AsyncHttpClient#BoundRequestBuilder = {
    method.toUpperCase(ENGLISH) match {
      case `GET`     ⇒ underlying.prepareGet _
      case `POST`    ⇒ underlying.preparePost _
      case `PUT`     ⇒ underlying.preparePut _
      case `DELETE`  ⇒ underlying.prepareDelete _
      case `HEAD`    ⇒ underlying.prepareHead _
      case `OPTIONS` ⇒ underlying.prepareOptions _
      case `CONNECT` ⇒ underlying.prepareConnect _
      case `PATCH`   ⇒ underlying.preparePatch _
      case `TRACE`   ⇒ underlying.prepareTrace _
    }
  }
  
  private def addParameters(method: String, params: Iterable[(String, String)])(req: AsyncHttpClient#BoundRequestBuilder) = {
    method.toUpperCase(ENGLISH) match {
      case `GET` | `DELETE` | `HEAD` | `OPTIONS` ⇒ params foreach { case (k, v) ⇒ req addQueryParameter (k, v) }
      case `PUT` | `POST`   | `PATCH`            ⇒ params foreach { case (k, v) ⇒ req addParameter (k, v) }
      case _                                     ⇒ // we don't care, carry on
    }
    req
  }
  
  private def addHeaders(headers: Map[String, String])(req: AsyncHttpClient#BoundRequestBuilder) = {
    headers foreach { case (k, v) => req.addHeader(k, v) }
    req
  }
  
  private val allowsBody = Vector(PUT, POST, PATCH)

  def submit[A](method: String, uri: String, params: Iterable[(String, String)], headers: Map[String, String], body: String)(f: => A) = {
    val u = URI.create(uri)
    val reqUri = if (u.isAbsolute) u else new URI("http", null, host, port, u.getPath, u.getQuery, u.getFragment)
    val req = (requestFactory(method) andThen (addHeaders(headers) _) andThen (addParameters(method, params) _))(reqUri.toASCIIString)
    u.getQuery.blankOption foreach { uu =>  
      MapQueryString.parseString(uu) foreach { case (k, v) => v foreach { req.addQueryParameter(k, _) } }
    }
    if (allowsBody.contains(method.toUpperCase(ENGLISH)) && body.nonBlank) req.setBody(body)
    val res = req.execute(async).get
    println("RESPONSE? %s" format (res != null))
    println("BODY: %s" format res.body)
    withResponse(res)(f)
  }
  
  private def async = new AsyncCompletionHandler[ClientResponse] {
    def onCompleted(response: Response) = {
      // copy the values out
      val cks = (response.getCookies map { cookie =>
        val cko = CookieOptions(cookie.getDomain, cookie.getPath, cookie.getMaxAge)
        cookie.getName -> org.scalatra.Cookie(cookie.getName, cookie.getValue)(cko)
      }).toMap
      println("COOKIES %s" format cks)
      val hdrs = (response.getHeaders.keySet() map { k => k -> response.getHeaders(k).mkString("; ")}).toMap
      println("HEADERS %s" format hdrs)
      val sts = ResponseStatus(response.getStatusCode, response.getStatusText)
      println("STATUS %s" format sts)
      val chst = Charset.forName("UTF-8")
      val is = response.getResponseBodyAsStream
      println("INPUTSTREAM? %s" format (is != null))
      val ctt = response.getContentType
      println("CONTENT TYPE %s" format ctt)
      val u = response.getUri
      println("URI: %s" format u)
      
      // use copies
      new ClientResponse {
        val cookies = cks

        val headers = hdrs

        val status = sts

        val contentType = ctt

        val charset = chst

        val inputStream = is

        val uri = u
      }
    }
  }
}