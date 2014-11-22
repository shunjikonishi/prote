import play.api.Application
import play.api.GlobalSettings
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.InternalServerError
import scala.concurrent.Future
import models.AppConfig
import scala.collection.JavaConversions._
import java.io.File
import exceptions.SSLNotSupportedException

object Global extends GlobalSettings {
  
  override def onStart(app: Application) {
    val dir = new File("proxy_logs")
    dir.mkdirs
    dir.listFiles.foreach(_.delete)
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    val consolePrefix = "/" + AppConfig.consoleContext + "/"
    if (request.path.startsWith(consolePrefix)) {
      val newRequest = request.copy(
        path = "/" + AppConfig.DEFAULT_CONTEXT + "/" + request.path.substring(consolePrefix.length),
        uri =  "/" + AppConfig.DEFAULT_CONTEXT + "/" + request.uri.substring(consolePrefix.length)
      )
      super.onRouteRequest(newRequest)
    } else if (request.headers.get("Upgrade").filter(_ == "websocket").isDefined) {
      Some(controllers.Application.proxyWS)
    } else {
      Some(controllers.Application.proxy)
    }
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    ex.getCause match {
      case e: SSLNotSupportedException => 
        Future.successful(InternalServerError(e.getMessage))
      case _ => 
        super.onError(request, ex)
    }
  }
}
