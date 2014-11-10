import play.api.GlobalSettings
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import models.AppConfig

object Global extends GlobalSettings {
  
  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    val consolePrefix = "/" + AppConfig.consoleContext + "/"
    if (request.path.startsWith(consolePrefix)) {
      val newRequest = request.copy(
        path = "/" + AppConfig.DEFAULT_CONTEXT + "/" + request.path.substring(consolePrefix.length),
        uri =  "/" + AppConfig.DEFAULT_CONTEXT + "/" + request.uri.substring(consolePrefix.length)
      )
      super.onRouteRequest(newRequest)
    } else {
      Some(controllers.Application.proxy)
    }
  }
}
