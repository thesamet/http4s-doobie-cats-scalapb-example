package spaces

import cats.data.{EitherT, Kleisli}
import cats.effect.IO
import cats.implicits._
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedService, Request}
import org.http4s.dsl.io._
import spaces.auth.User
import spaces.services.{DirectoryService, StaticDirectoryService}

/** Helpers to construct our authentication middleware based on the directory service. */
object Auth {
  // Given a DirectoryService, returns a function that knows how to either a user from a request (or an error string)
  private def retrieveUser(directoryService: DirectoryService): (Request[IO] => IO[Either[String, User]]) = {
    request: Request[IO] =>
      EitherT(
        request.headers
          .get(Authorization)
          .map(_.value)
          .toRight("Couldn't find an Authorization header")
          .traverse(directoryService.fromApiToken)
      ).subflatMap {
        case Some(x) => Right(x)
        case None => Left("Invalid token")
      }.value
  }

  private val onFailure: AuthedService[String, IO] = AuthedService[String, IO] {
    case req => org.http4s.dsl.io.Forbidden(req.authInfo)
  }

  def middleware(directoryService: DirectoryService) =
      AuthMiddleware(
        Kleisli(retrieveUser(StaticDirectoryService)), onFailure
      )
}
