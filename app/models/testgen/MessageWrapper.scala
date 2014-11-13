package models.testgen

import models.RequestMessage
import models.ResponseMessage

class MessageWrapper(val request: RequestMessage, val response: ResponseMessage) {

  def path = request.requestLine.path
  def host = request.getHeader("Host").getOrElse("UnkownHost")
  def method = request.requestLine.method

  def hasRequestBody = request.body.isDefined
  def requestContentType = request.contentType

  def requestHeaders(indent: Int) = "{}"
  def requestBody(indent: Int) = "{}"

  def statusCode = response.statusLine.code
}
