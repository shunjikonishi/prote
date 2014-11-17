package models

import roomframework.command._
import play.api.libs.json._
import java.util.UUID
import java.io.File
import models.testgen.MochaTestGenerator
import models.testgen.MessageWrapper

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
    addHandler("generateTest") { command =>
      val desc = (command.data \ "desc").asOpt[String].getOrElse("Auto generated test")
      val ids = (command.data \ "ids") match {
        case JsArray(seq) => seq.map(_.as[String])
        case _ => throw new IllegalArgumentException()
      }
      val sm = StorageManager
      val messages = ids.map(id => MessageWrapper(sm.getRequestMessage(id), sm.getResponseMessage(id)))
      val dir = new java.io.File("test")
      messages.zipWithIndex.foreach { case(msg, idx) =>
        msg.request.copyTo(dir, idx.toString)
        msg.response.copyTo(dir, idx.toString)
      }
      val script = MochaTestGenerator.generate(desc, ids)
      val id = UUID.randomUUID.toString
      StorageManager.saveToFile(id + ".js", script)
      command.text(id)
    }
    addHandler("test") { command =>
      val desc = (command.data \ "desc").asOpt[String].getOrElse("Auto generated test")
      val ids = (command.data \ "ids") match {
        case JsArray(seq) => seq.map(_.as[String])
        case _ => throw new IllegalArgumentException()
      }
      val sm = new StorageManager(new File("test"), AppConfig.cookieName)
      val script = new MochaTestGenerator(sm).generate(desc, ids)
      val id = UUID.randomUUID.toString
      sm.saveToFile(id + ".js", script)
      command.text(id)
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