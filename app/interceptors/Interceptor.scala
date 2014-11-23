package interceptors

import models.RequestMessage
import models.ResponseMessage

trait Interceptor {

  def hookRequest(request: RequestMessage): Either[RequestMessage, ResponseMessage] = Left(request)

  def hookResponse(request: RequestMessage, response: ResponseMessage): ResponseMessage = response
}