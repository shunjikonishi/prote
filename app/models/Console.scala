package models

import roomframework.command._
import play.api.libs.json._
import java.util.UUID
import java.io.File

class Console(pm: ProxyManager, sessionId: String) extends CommandInvoker {

  private var _closed = false
  def closed = _closed

  private def init = {
    val sm = pm.storageManager
    
    addHandler("noop") { command =>
      CommandResponse.None
    }
    addHandler("request") { command =>
      val id = (command.data \ "id").as[String]
      val protocol = (command.data \ "protocol").as[String]
      val expand = (command.data \ "expand").asOpt[Boolean].getOrElse(false)
      val text = if (protocol.startsWith("http")) {
        val msg = sm.getRequestMessage(id)
        msg.toString(expand)
      } else {
        webSocketText(id, protocol == "wss", true, expand)
      }
      command.text(text)
    }
    addHandler("response") { command =>
      val id = (command.data \ "id").as[String]
      val protocol = (command.data \ "protocol").as[String]
      val expand = (command.data \ "expand").asOpt[Boolean].getOrElse(false)
      val text = if (protocol.startsWith("http")) {
        val msg = sm.getResponseMessage(id)
        msg.toString(expand)
      } else {
        webSocketText(id, protocol == "wss", false, expand)
      }
      command.text(text)
    }
    addHandler("generateTest") { command =>
      val name = (command.data \ "filename").asOpt[String].getOrElse("test")
      val desc = (command.data \ "description").asOpt[String].getOrElse("Auto generated test")
      val kind = (command.data \ "kind").asOpt[String].getOrElse("mocha")
      val external = (command.data \ "external").asOpt[String].filter(_.length > 0)
      val ids = (command.data \ "ids") match {
        case JsArray(seq) => seq.map(_.as[String])
        case _ => throw new IllegalArgumentException()
      }
      val id = pm.testGenerator(kind).generate(name, desc, ids, external)
      command.text(id)
    }
    addHandler("regenerateTest") { command =>
      val id = (command.data \ "id").as[String]
      val name = (command.data \ "filename").asOpt[String].getOrElse("test")
      val desc = (command.data \ "description").asOpt[String].getOrElse("Auto generated test")
      val kind = (command.data \ "kind").asOpt[String].getOrElse("mocha")
      val external = (command.data \ "external").asOpt[String].filter(_.length > 0)

      pm.regenerator(id, kind).map { gen =>
        val ids = new File(pm.testLogs, id)
          .listFiles
          .filter(_.getName.endsWith(".request.headers"))
          .map(_.getName.takeWhile(_ != '.').toInt)
          .sorted
          .map(_.toString)
        gen.generate(name, desc, ids, external, id)
        command.text(id)
      }.getOrElse {
        command.json(JsObject(Seq(
          "error" -> JsString("Not found")
        )))
      }
    }
  }

  override protected def onDisconnect: Unit = {
    _closed = true
  }

  private def webSocketText(id: String, ssl: Boolean, outgoing: Boolean, expand: Boolean): String = {
    val msg = pm.storageManager.getWebSocketMessage(id, ssl, outgoing)
    msg.map { msg =>
      if (expand) {
        try {
          Json.prettyPrint(Json.parse(msg.body))
        } catch {
          case e: Throwable =>
            msg.body
        }
      } else {
        msg.body
      }
    }.getOrElse("")
  }


  def process(id: String, request: RequestMessage, response: ResponseMessage, time: Long): Unit = {
    val reqKind = request.kind
    val resKind = response.kind match {
      case MessageKind.None | MessageKind.Unknown => request.responseKindFromPath
      case kind => kind
    }
    val command = new CommandResponse("process", JsObject(Seq(
      "id" -> JsString(id),
      "protocol" -> JsString(request.host.protocol),
      "method" -> JsString(request.requestLine.method),
      "uri" -> JsString(request.requestLine.uri),
      "reqKind" -> JsString(reqKind.toString),
      "resKind" -> JsString(resKind.toString),
      "status" -> JsNumber(response.statusLine.code),
      "time" -> JsNumber(time)
    )))
    send(command)
  }

  def process(id: String, msg: WebSocketMessage): Unit = {
    val kind = if (msg.body.startsWith("{") && msg.body.endsWith("}")) MessageKind.Json else MessageKind.Unknown
    val kindKey = if (msg.outgoing) "reqKind" else "resKind"
    val desc = (if (msg.outgoing) "--> " else "<-- ") + {
      if (msg.body.length() < 100) msg.body else msg.body.substring(0, 100) + "..."
    }
    val command = new CommandResponse("processWS", JsObject(Seq(
      "id" -> JsString(id),
      "protocol" -> JsString(msg.protocol),
      "outgoing" -> JsBoolean(msg.outgoing),
      "desc" -> JsString(desc),
      kindKey -> JsString(kind.toString)
    )))
    send(command)
  }

  init
}