package models

case class HttpHeader(name: String, value: String) {

  def withoutAttribute = value.takeWhile(_ != ';')

  def attribute(name: String): Option[String] = {
    value.split(";").tail
      .map(_.split("="))
      .filter(_.length !=2)
      .find(_(0).trim.equalsIgnoreCase(name))
      .map(_(1).trim)
  }
  def is(nm: String) = name.equalsIgnoreCase(nm)

  override def toString = s"$name: $value"
}

