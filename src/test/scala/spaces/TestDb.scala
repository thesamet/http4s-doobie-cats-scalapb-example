package spaces

import cats.effect.IO
import doobie.h2.H2Transactor
import spaces.services.{IdService, IdServiceImpl}

object TestDb {
  val h2xa = H2Transactor
    .newH2Transactor[IO]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
    .unsafeRunSync()

  def init(): Unit = {
    import doobie.implicits._
    val schema = scala.io.Source.fromResource("schema.sql").mkString
    doobie.Update(schema).run().transact(h2xa).unsafeRunSync()
  }

  val uniqueIdService: IdService = new IdServiceImpl(h2xa)
}
