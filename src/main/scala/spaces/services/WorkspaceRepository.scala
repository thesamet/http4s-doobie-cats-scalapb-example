package spaces.services

import cats.data.{NonEmptyVector, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.enum.SqlState
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import spaces.Id
import spaces.api.protos._

/** Workspace storage service.
  *
  * Provides get/store functionality for workspaces.
  */
trait WorkspaceRepository {
  def get(id: Id[Workspace]): IO[Option[Workspace]]

  def store(workspace: Workspace): IO[Workspace]

  // Returns all workspaces in the repository. Obviously this is drastically over-simplified: no pagination,
  // filtering, sorting, etc.
  def all: IO[Vector[Workspace]]
}

/** Concrete implementation that is backed by a SQL database.
  *
  * Supports optimistic concurrency and validates the constraint that components have
  * exactly one parent.
  */
class WorkspaceRepositoryImpl(xa: Transactor[IO]) extends WorkspaceRepository {
  import doobie.implicits._

  def get(id: Id[Workspace]): IO[Option[Workspace]] = {
    val select =
      sql"SELECT bin FROM `workspaces` WHERE workspace_id=${id.id} ORDER BY VERSION DESC LIMIT 1"
        .query[Array[Byte]]
        .option
    OptionT(select.transact(xa)).map(Workspace.parseFrom).value
  }

  def store(_workspace: Workspace): IO[Workspace] = {
    val workspace = _workspace
      .withLastModified(System.currentTimeMillis())
      .withVersion(_workspace.version + 1)

    // Optimistic update (transaction would fail if there is a version conflict)
    val insertWorkspace =
      sql"""INSERT INTO `workspaces` (workspace_id, version, bin)
      VALUES (${workspace.id}, ${workspace.version}, ${workspace.toByteArray})""".update.run

    // Prepare a list of the references to remote components, to be inserted
    // to the constraints table.
    val remoteRefs: Vector[String] =
      ((workspace.repositories.map(r => s"repos:${r.ref}") ++ (for {
        envs <- workspace.environments
        db <- envs.databases
      } yield s"db:${db.ref}"))).toVector

    // We are being a little inefficient here: each time we store a workspace, we delete all
    // its existing links and then re-creating the ones that we need.
    val deleteConstraints =
      sql"DELETE FROM `constraints` WHERE workspace_id=${workspace.id.id}".update.run
        .map(_ => ())

    val updateConstraints: ConnectionIO[Unit] =
      NonEmptyVector.fromVector(remoteRefs) match {
        case Some(nonEmptyRefs) =>
          val insertConstraints =
            Update[(Long, String)](
              "INSERT INTO `constraints` (workspace_id, remote_ref) VALUES(?, ?)")
              .updateMany(remoteRefs.map(rr => (workspace.id.id, rr)))

          for {
            _ <- deleteConstraints
            _ <- insertConstraints
          } yield ()

        case None =>
          deleteConstraints
      }

    val program = for {
      _ <- insertWorkspace
      _ <- updateConstraints
    } yield workspace

    program
      .transact(xa)
      .attemptSomeSqlState({
        case SqlState(MySQLIntegrityError | H2IntegrityError) =>
          new DataConflictException(
            "Component is already associated with another workspace")
      })
      .flatMap(IO.fromEither)
  }

  def all: IO[Vector[Workspace]] = {
    sql"""SELECT bin from workspaces w1 WHERE
            w1.version = (SELECT max(version) FROM workspaces w2 WHERE w2.workspace_id = w1.workspace_id)"""
      .query[Array[Byte]]
      .to[Vector]
      .transact(xa)
      .map(_.map(Workspace.parseFrom))
  }

  // Vendor-specific error code for unique index constraints.
  val MySQLIntegrityError = "23000"
  val H2IntegrityError = "23505"
}
