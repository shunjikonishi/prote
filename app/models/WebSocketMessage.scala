package models

case class WebSocketMessage(ssl: Boolean, outgoing: Boolean, body: String) {
  def protocol = if (ssl) "wss" else "ws"
  def incoming: Boolean = !outgoing
}