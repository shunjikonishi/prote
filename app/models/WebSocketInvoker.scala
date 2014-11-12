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
      val msg = StorageManager.getRequestMessage(id)
      command.text(msg.toString)
    }
    addHandler("response") { command =>
      val id = (command.data \ "id").as[String]
      val msg = StorageManager.getResponseMessage(id)
      command.text(msg.toString)
    }
  }

  def process(id: String, request: RequestMessage, response: ResponseMessage, time: Long) = {
    val command = new CommandResponse("process", JsObject(Seq(
      "id" -> JsString(id),
      "method" -> JsString(request.requestLine.method),
      "uri" -> JsString(request.requestLine.uri),
      "status" -> JsNumber(response.statusLine.code),
      "time" -> JsNumber(time)
    )))
    send(command)
  }

  init
}