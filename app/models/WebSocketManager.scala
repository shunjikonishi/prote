package models

class WebSocketManager {
  var map: Map[String, WebSocketInvoker] = Map.empty

  def getInvoker(sessionId: String): WebSocketInvoker = {
    map.get(sessionId) match {
      case Some(x) => x
      case None =>
        val ret = new WebSocketInvoker(sessionId)
        map += (sessionId -> ret)
        ret
    }
  }
}

object WebSocketManager extends WebSocketManager

