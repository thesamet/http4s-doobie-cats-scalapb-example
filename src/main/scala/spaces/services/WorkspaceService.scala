package spaces.services

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import spaces.{Id, UserGroupId}
import spaces.auth.User
import spaces.api.protos._

/** Provides the business logic for our service.
  *
  * The web API delegates HTTP calls to this interface.
  *
  * All our methods return an IO[Workspace] which represents a suspended computation
  * that results in * a value of type A. The computation is potentially asynchronous and
  * likely contains side-effects (such as writing or reading from remote services).
  * The computation inside an IO is suspended: it is only evaluated on-demand later (in our
  * case, not even by our own code, but by the HTTP framework)
  */
trait WorkspaceService {

  /** Returns a workspace by id, but only if the given user is allowed to view it */
  def getWorkspace(id: Id[Workspace], user: User): IO[Workspace]

  /** Lists all workspaces available to us */
  def listWorkspaces(user: User): IO[Seq[Id[Workspace]]]

  /** Creates a new workspace */
  def newWorkspace(workspaceId: Id[Workspace],
                   name: String,
                   owners: Seq[UserGroupId]): IO[Workspace]

  /** Deletes a workspace */
  def deleteWorkspace(workspace: Workspace): IO[Workspace]

  /** Adds an environment to an existing workspace */
  def addEnvironment(workspace: Workspace,
                     environmentId: Id[Environment],
                     name: String): IO[Workspace]

  /** Deletes an environment to an existing workspace */
  def deleteEnvironment(workspace: Workspace,
                        environmentId: Id[Environment]): IO[Workspace]

  // Link and unlink things. A concrete implementation would have a way to obtain metadata
  // about remote source repositories and databases.
  def linkSourceRepository(workspace: Workspace,
                           repositoryRef: String): IO[Workspace]

  def unlinkSourceRepository(workspace: Workspace,
                             repositoryRef: String): IO[Workspace]

  def linkDatabase(workspace: Workspace,
                   environmentId: Id[Environment],
                   dbRef: String): IO[Workspace]

  def unlinkDatabase(workspace: Workspace,
                     environment: Id[Environment],
                     dbRef: String): IO[Workspace]
}

/** The concrete implementation is written in terms of abstract interfaces of the
  * the workspace repository and the infrastructure service.
  */
class WorkspaceServiceImpl(repo: WorkspaceRepository,
                           infraService: InfraService)
    extends WorkspaceService {

  def getWorkspace(id: Id[Workspace], user: User): IO[Workspace] = {
    OptionT(repo.get(id))
      .filterNot(_.isDeleted)
      .getOrElseF(IO.raiseError(ObjectNotFound("Workspace not found")))
      .ensure(NoAccess("Access forbidden"))(
        _.groupIds.toSet.intersect(user.groupIds.toSet).nonEmpty)
  }

  def listWorkspaces(user: User): IO[Seq[Id[Workspace]]] = {
    repo.all.map(_.collect {
      case ws if !ws.isDeleted && ws.groupIds.toSet.intersect(user.groupIds.toSet).nonEmpty =>
        ws.id
    })
  }

  def ensure(cond: Boolean)(error: Throwable) =
    if (!cond) IO.raiseError(error) else IO.pure(())

  override def newWorkspace(workspaceId: Id[Workspace],
                            name: String,
                            owners: Seq[UserGroupId]): IO[Workspace] =
    repo.store(
      Workspace(
        workspaceId,
        timeCreated = System.currentTimeMillis(),
        name = name,
        groupIds = owners,
        repositories = Seq.empty,
        environments = Seq.empty
      )
    )

  override def deleteWorkspace(workspace: Workspace): IO[Workspace] = {
    repo.store(
      workspace.withIsDeleted(true)
    )
  }

  override def addEnvironment(workspace: Workspace,
                              environmentId: Id[Environment],
                              name: String): IO[Workspace] =
    for {
      _ <- ensure(!workspace.environments.exists(_.name == name))(
        DuplicateName("Environment with same name already exists")
      )
      _ <- ensure(!workspace.environments.exists(_.id == environmentId))(
        DuplicateName("Environment with same id exists")
      )
      _ <- ensure(name.nonEmpty)(InvalidRequest("Name must be non-empty"))
      env = Environment(environmentId, name = name)
      ws <- repo.store(
        workspace
          .addEnvironments(env)
      )
    } yield ws

  override def deleteEnvironment(workspace: Workspace,
                                 environmentId: Id[Environment]): IO[Workspace] =
    if (!workspace.environments.exists(_.id == environmentId))
      IO.raiseError(
        new ObjectNotFound("Environment with given id was not found"))
    else
      repo.store(workspace
        .withEnvironments(workspace.environments.filter(_.id != environmentId)))

  override def linkSourceRepository(workspace: Workspace,
                                    repositoryRef: String): IO[Workspace] =
    for {
      _ <- ensure(repositoryRef.nonEmpty)(
        InvalidRequest("ref must be non-empty"))
      _ <- ensure(!workspace.repositories.exists(_.ref == repositoryRef))(
        InvalidRequest("Repository is already linked to this workspace"))
      sourceRepo <- infraService.getSourceRepository(repositoryRef)
      st <- repo.store(workspace.addRepositories(sourceRepo))
    } yield st

  override def unlinkSourceRepository(workspace: Workspace,
                                      repositoryRef: String): IO[Workspace] =
    for {
      _ <- ensure(workspace.repositories.exists(_.ref == repositoryRef))(
        ObjectNotFound("Source repository was not found"))
      _ <- ensure(repositoryRef.nonEmpty)(
        InvalidRequest("ref must be non-empty"))
      ws <- repo.store(
        workspace.withRepositories(
          workspace.repositories.filter(_.ref != repositoryRef)))
    } yield ws

  private def replaceEnvironment(ws: Workspace, newEnv: Environment) = {
    // helper function that replace the existing environment that matches
    // newEnv.id with newEnv, while keeping the order of the environments.
    ws.copy(environments = ws.environments.map {
      case env if env.id != newEnv.id => env
      case _                          => newEnv
    })
  }

  override def linkDatabase(workspace: Workspace,
                            environmentId: Id[Environment],
                            dbRef: String): IO[Workspace] =
    workspace.environments.find(_.id == environmentId) match {
      case None =>
        IO.raiseError(new ObjectNotFound("Environment was not found"))
      case Some(env) =>
        for {
          _ <- ensure(!env.databases.exists(_.ref == dbRef))(
            InvalidRequest("Database is already linked to this environment"))
          db <- infraService.getDatabase(dbRef)
          ws <- repo.store(replaceEnvironment(workspace, env.addDatabases(db)))
        } yield ws
    }

  override def unlinkDatabase(workspace: Workspace,
                              environmentId: Id[Environment],
                              dbRef: String): IO[Workspace] =
    for {
      updatedEnv <- IO.fromEither(
        workspace.environments
          .find(_.id == environmentId)
          .toRight(new ObjectNotFound("Environment was not found"))
          .ensure(new ObjectNotFound("Database was not found"))(
            _.databases.exists(_.ref == dbRef))
          .map(e => e.withDatabases(e.databases.filter(_.ref != dbRef)))
      )
      ws <- repo.store(replaceEnvironment(workspace, updatedEnv))
    } yield ws
}
