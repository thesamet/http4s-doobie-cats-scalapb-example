package spaces.services

import cats.effect.IO
import spaces.infra.protos.Database.DatabaseType
import spaces.infra.protos.SourceRepository.SourceRepositoryType
import spaces.infra.protos.{Database, SourceRepository}

/** Represents an API to obtain remote components that are managed by other services */
trait InfraService {
  def getDatabase(url: String): IO[Database]

  def getSourceRepository(url: String): IO[SourceRepository]
}

/** Concrete static implementation. */
object StaticInfraService extends InfraService {
  def getDatabase(url: String): IO[Database] = url match {
    case "mysql://xyz" => IO.pure(Database(url, DatabaseType.MYSQL))
    case "mysql://abc" => IO.pure(Database(url, DatabaseType.MYSQL))
    case "pg://xyz"    => IO.pure(Database(url, DatabaseType.POSTGRES))
    case _             => IO.raiseError(new ObjectNotFound("Database not found"))
  }

  def getSourceRepository(url: String): IO[SourceRepository] = url match {
    case "git://xyz" => IO.pure(SourceRepository(url, SourceRepositoryType.GIT))
    case "svn://xyz" =>
      IO.pure(SourceRepository(url, SourceRepositoryType.SUBVERSION))
    case _ => IO.raiseError(new ObjectNotFound("Source repository not found"))
  }
}
