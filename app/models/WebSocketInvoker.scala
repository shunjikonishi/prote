package models

import roomframework.command._
import play.api.libs.json._

class WebSocketInvoker(sessionId: String) extends CommandInvoker {

  private def init = {
    addHandler("noop") { command =>
      CommandResponse.None
    }
    addHandler("request") { command =>
      val id = (command.data \ "id").as[String]
      val prettyPrint = (command.data \ "prettyPrint").asOpt[Boolean].getOrElse(false)
      val msg = StorageManager.getRequestMessage(id)
      command.text(msg.toString(prettyPrint))
    }
    addHandler("response") { command =>
      val id = (command.data \ "id").as[String]
      val prettyPrint = (command.data \ "prettyPrint").asOpt[Boolean].getOrElse(false)
      val msg = StorageManager.getResponseMessage(id)
      command.text(msg.toString(prettyPrint))
    }
  }

  def process(id: String, request: RequestMessage, response: ResponseMessage, time: Long) = {
    val reqKind = request.kind
    val resKind = response.kind match {
      case MessageKind.None | MessageKind.Unknown => request.responseKindFromPath
      case kind => kind
    }
    val command = new CommandResponse("process", JsObject(Seq(
      "id" -> JsString(id),
      "method" -> JsString(request.requestLine.method),
      "uri" -> JsString(request.requestLine.uri),
      "reqKind" -> JsString(reqKind.toString),
      "resKind" -> JsString(resKind.toString),
      "status" -> JsNumber(response.statusLine.code),
      "time" -> JsNumber(time)
    )))
    send(command)
  }

  init
}