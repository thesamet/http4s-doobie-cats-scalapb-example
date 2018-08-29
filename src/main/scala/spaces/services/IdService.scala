package spaces.services

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import spaces.Id

/** An internal private service that allocates unique ids for objects we own */
trait IdService {
  def newId[A]: IO[Id[A]]
}

/** Concrete implementation that is backed up by a SQL auto-incremented key */
class IdServiceImpl(xa: Transactor[IO]) extends IdService {
  override def newId[A]: IO[Id[A]] = {
    sql"INSERT INTO `key_gen` VALUES ()".update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)
      .map(Id[A])
  }
}
