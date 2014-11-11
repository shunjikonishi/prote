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
      CommandResponse.None
    }
    addHandler("response") { command =>
      val id = (command.data \ "id").as[String]
      val msg = StorageManager.getResponseMessage(id)
      CommandResponse.None
    }
  }

  def process(request: RequestMessage, response: ResponseMessage, time: Long) = {
    val command = new CommandResponse("process", JsObject(Seq(
      "request" -> JsString(request.requestLine.toString),
      "status" -> JsNumber(response.statusLine.code),
      "time" -> JsNumber(time)
    )))
    send(command)
  }

  init
}