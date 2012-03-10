package org.scalatra

import util.RicherString._

import scala.collection.mutable.{Map, HashMap}

trait ScalatraHandler extends ScalatraBase {
  type RequestT = Request
  type ResponseT = Response
  type ApplicationContextT = DummyApplicationContext.type
  type ConfigT = DummyConfig.type

  override def addSessionId(uri: String) = uri
  override def routeBasePath = "/"
  var doNotFound: Action = () => { status = 404 }
  override def requestPath = request.pathInfo.nonBlank getOrElse { "/" }

  override def requestWrapper(req: RequestT) = req
  override def responseWrapper(res: ResponseT) = res
  
  object DummyApplicationContext extends HashMap[String, AnyRef] with ApplicationContext {
    def resource(path: String) = None
    def initParameters = Map.empty
    def contextPath = ""
    def mount(handler: Handler, urlPattern: String, name: String) {}
  }

  override def applicationContextWrapper(context: ApplicationContextT) =
    DummyApplicationContext

  object DummyConfig extends Config {
    def initParameters = scala.collection.immutable.Map.empty
    def context = DummyApplicationContext
  }

  override def configWrapper(config: ConfigT) = DummyConfig
}