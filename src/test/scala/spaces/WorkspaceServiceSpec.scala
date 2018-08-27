package spaces

import cats.effect.IO
import org.http4s.{
  EntityDecoder,
  Header,
  Headers,
  Method,
  Request,
  Status,
  Uri
}
import org.http4s.circe._
import org.http4s.dsl.io._
import org.scalatest.FlatSpec
import io.circe.literal._
import spaces.api.protos.{
  CreateEnvironmentRequest,
  LinkDatabaseRequest,
  LinkSourceRepositoryRequest,
  Workspace
}
import spaces.infra.protos.Database
import spaces.infra.protos.Database.DatabaseType.MYSQL

// An end-to-end test, testing some of the stories
class WorkspaceServiceSpec extends FlatSpec {
  TestDb.init()

  val service = ApiServer.makeRealService(TestDb.h2xa)
  import spaces.api.JsonProtoUtils._

  def runRequest[A](r: Request[IO], expectedStatus: Status)(
      implicit ed: EntityDecoder[IO, A]) = {
    val actualResp = service.run(r).value.map(_.get).unsafeRunSync()
    assert(actualResp.status == expectedStatus)
    actualResp.as[A].unsafeRunSync
  }

  def request(method: Method, uri: String) =
    Request[IO](method = method,
                uri = Uri.unsafeFromString(uri),
                headers = Headers(Header("Authorization", "Bearer t1")))

  "workspace service" should "allow story" in {
    val resp = runRequest[Workspace](
      request(POST, "/createWorkspace")
        .withBody(json"""{"name": "myws", "groupRefs": ["g1"]}""")
        .unsafeRunSync(),
      Ok
    )

    val wsId = resp.id

    val ws2 = runRequest[Workspace](request(GET, s"/workspaces/${wsId.id}"), Ok)

    assert(ws2.clearOwners == resp.clearOwners)
    val ws3 = runRequest[Workspace](
      request(POST, s"/linkSourceRepository")
        .withBody(LinkSourceRepositoryRequest(workspaceId = wsId, "git://xyz"))
        .unsafeRunSync(),
      Ok
    )
    assert(ws3.repositories.exists(_.ref == "git://xyz"))

    // inexistent
    val ws4 = runRequest[String](
      request(POST, s"/linkSourceRepository")
        .withBody(
          LinkSourceRepositoryRequest(workspaceId = wsId, "git://invalid"))
        .unsafeRunSync(),
      NotFound
    )

    // unlink
    val ws5 = runRequest[Workspace](
      request(POST, s"/unlinkSourceRepository")
        .withBody(LinkSourceRepositoryRequest(workspaceId = wsId, "git://xyz"))
        .unsafeRunSync(),
      Ok
    )
    assert(!ws5.repositories.exists(_.ref == "git://xyz"))

    // create environment
    val ws6 = runRequest[Workspace](
      request(POST, s"/createEnvironment")
        .withBody(CreateEnvironmentRequest(workspaceId = wsId, name = "myenv"))
        .unsafeRunSync(),
      Ok
    )
    val envId = ws6.environments.find(_.name == "myenv").map(_.id).get

    // link database
    val ws7 = runRequest[Workspace](
      request(POST, s"/linkDatabase")
        .withBody(
          LinkDatabaseRequest(workspaceId = wsId,
                              environmentId = envId,
                              ref = "mysql://xyz"))
        .unsafeRunSync(),
      Ok
    )
    assert(
      ws7.environments
        .find(_.name == "myenv")
        .get
        .databases
        .find(_.ref == "mysql://xyz")
        .get ==
        Database(ref = "mysql://xyz", MYSQL))

    // unlink database
    val ws8 = runRequest[Workspace](
      request(POST, s"/unlinkDatabase")
        .withBody(
          LinkDatabaseRequest(workspaceId = wsId,
                              environmentId = envId,
                              ref = "mysql://xyz"))
        .unsafeRunSync(),
      Ok
    )
    assert(ws8.environments.find(_.name == "myenv").get.databases.isEmpty)
  }
}
