package models

case class HostInfo(name: String, ssl: Boolean) {
  def protocol = if (ssl) "https" else "http"
}