package models.testgen

import models.RequestMessage
import models.ResponseMessage

case class MessageWrapper(request: RequestMessage, response: ResponseMessage) {

  def path = request.requestLine.path
  def host = request.host.name
  def protocol = request.host.protocol
  def method = request.requestLine.method

  def hasRequestBody = request.body.isDefined
  def requestContentType = request.contentType

  def requestHeaders(indent: Int) = "{}"
  def requestBody(indent: Int) = "{}"

  def statusCode = response.statusLine.code
}
