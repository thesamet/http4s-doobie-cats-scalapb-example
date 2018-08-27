package spaces

import cats.effect.IO
import doobie.util.transactor.Transactor
import fs2.StreamApp
import org.http4s.AuthedService
import org.http4s.server.blaze.BlazeBuilder
import spaces.api.Api
import spaces.auth.User
import spaces.services._

case class DatabaseConfig(driver: String, url: String, user: String, password: String)

case class Config(host: String, port: Int, database: DatabaseConfig)

object ApiServer extends StreamApp[IO] {

  def authedService(xa: Transactor[IO]): AuthedService[User, IO] = {
    val repo = new WorkspaceRepositoryImpl(xa)
    val workspaceService = new WorkspaceServiceImpl(repo, StaticInfraService)

    new WorkspaceServiceImpl(repo, StaticInfraService)
    Api.buildService(
      workspaceService,
      new IdServiceImpl(xa),
      StaticDirectoryService)
  }

  def makeRealService(xa: Transactor[IO]) = {
    Auth.middleware(StaticDirectoryService)(authedService(xa))
  }

  def transactor(cfg: DatabaseConfig) =
    Transactor.fromDriverManager[IO](
      cfg.driver,
      cfg.url,
      cfg.user,
      cfg.password
    )

  def stream(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val config = pureconfig.loadConfig[Config] match {
      case Right(c) => c
      case Left(e) =>
        throw new RuntimeException("Config error: " + e)
    }

    BlazeBuilder[IO]
      .bindHttp(config.port, config.host)
      .mountService(makeRealService(transactor(config.database)), "/")
      .serve
  }
}

