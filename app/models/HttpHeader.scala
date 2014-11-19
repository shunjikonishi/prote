package models

case class HttpHeader(name: String, value: String) {
  override def toString = s"$name: $value"
}

