package models

import roomframework.command._
import play.api.libs.json._
import java.util.UUID
import java.io.File
import models.testgen.TestGenerator
import models.testgen.MochaTestGenerator
import models.testgen.MessageWrapper

class WebSocketInvoker(sessionId: String) extends CommandInvoker {

  private var _closed = false
  def closed = _closed

  private def init = {
    addHandler("noop") { command =>
      CommandResponse.None
    }
    addHandler("request") { command =>
      val id = (command.data \ "id").as[String]
      val expand = (command.data \ "expand").asOpt[Boolean].getOrElse(false)
      val msg = StorageManager.getRequestMessage(id)
      command.text(msg.toString(expand))
    }
    addHandler("response") { command =>
      val id = (command.data \ "id").as[String]
      val expand = (command.data \ "expand").asOpt[Boolean].getOrElse(false)
      val msg = StorageManager.getResponseMessage(id)
      command.text(msg.toString(expand))
    }
    addHandler("generateTest") { command =>
      val name = (command.data \ "filename").asOpt[String].getOrElse("test")
      val desc = (command.data \ "description").asOpt[String].getOrElse("Auto generated test")
      val kind = (command.data \ "kind").asOpt[String].getOrElse("mocha")
      val ids = (command.data \ "ids") match {
        case JsArray(seq) => seq.map(_.as[String])
        case _ => throw new IllegalArgumentException()
      }
      val id = TestGenerator(kind).generate(name, desc, ids)
      command.text(id)
    }
    addHandler("regenerateTest") { command =>
      val id = (command.data \ "id").as[String]
      val name = (command.data \ "filename").asOpt[String].getOrElse("test")
      val desc = (command.data \ "description").asOpt[String].getOrElse("Auto generated test")
      val kind = (command.data \ "kind").asOpt[String].getOrElse("mocha")

      TestGenerator.regenerator(id, kind).map { gen =>
        val maxId = new File(TestGenerator.baseDir, id)
          .listFiles
          .filter(_.getName.endsWith(".request.headers"))
          .map(_.getName.takeWhile(_ != '.').toInt)
          .max
        val ids = List.range(1, maxId + 1).map(_.toString)
        gen.generate(id, name, desc, ids)
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


  def process(id: String, request: RequestMessage, response: ResponseMessage, time: Long) = {
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

  init
}