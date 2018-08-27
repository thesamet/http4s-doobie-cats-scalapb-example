package spaces.services

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import spaces.Ids.{EnvironmentId, WorkspaceId}

/** An internal private service that allocates unique ids for objects we own */
trait IdService {
  def newUniqueId: IO[Long]

  def newWorkspaceId = newUniqueId.map(WorkspaceId)

  def newEnvironmentId = newUniqueId.map(EnvironmentId)
}

/** Concrete implementation that is backed up by a SQL auto-incremented key */
class IdServiceImpl(xa: Transactor[IO]) extends IdService {
  override def newUniqueId: IO[Long] = {
    sql"INSERT INTO `key_gen` VALUES ()".update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)
  }
}
