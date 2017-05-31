package com.advancedtelematic.campaigner.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.syntax.show._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.libats.data.Namespace
import scala.concurrent.{ExecutionContext, Future}

trait DirectorClient {

  def setMultiUpdateTarget(ns: Namespace,
                           update: UpdateId,
                           devices: Seq[DeviceId]): Future[Seq[DeviceId]]
}

class DirectorHttpClient(uri: Uri)
    (implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer)
    extends HttpClient("director", uri) with DirectorClient {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.syntax._

  override def setMultiUpdateTarget(ns: Namespace,
                                    update: UpdateId,
                                    devices: Seq[DeviceId]): Future[Seq[DeviceId]] = {
    val path   = uri.path / "api" / "v1" / "admin" / "multi_target_updates" / update.show
    val entity = HttpEntity(ContentTypes.`application/json`, devices.asJson.noSpaces)
    val req    = HttpRequest(
      method = HttpMethods.PUT,
      uri    = uri.withPath(path),
      entity = entity
    )
    execHttp[Seq[DeviceId]](ns, req)
  }

}