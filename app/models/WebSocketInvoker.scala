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
      val msg = StorageManager.getRequestMessage(id)
      CommandResponse.None
    }
  }

  init
}