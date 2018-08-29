package spaces

import org.scalatest.FlatSpec
import spaces.api.protos._
import spaces.infra.protos.SourceRepository
import spaces.services._

class WorkspaceRepositorySpec extends FlatSpec {
  TestDb.init()
  def uniqueId = TestDb.uniqueIdService.newId[Workspace].unsafeRunSync()

  val repo: WorkspaceRepository = new WorkspaceRepositoryImpl(TestDb.h2xa)

  "workspace repository" should "allow read followed by store" in {
    val wsId = uniqueId
    val ws = Workspace(wsId,
                       repositories =
                         Seq(SourceRepository(ref = s"ref/${uniqueId}")))
    val out = (for {
      _ <- repo.store(ws)
      upd <- repo.get(wsId)
    } yield upd).unsafeRunSync()

    assert(ws.withVersion(1) == out.get.withLastModified(0))
  }

  it should "disallow two insertions of the same version (optimistic locking)" in {
    val wsId = uniqueId
    val ws = Workspace(wsId,
                       version = 7,
                       repositories =
                         Seq(SourceRepository(ref = s"ref/${uniqueId}")))
    repo.store(ws).unsafeRunSync()
    intercept[DataConflictException] { repo.store(ws).unsafeRunSync() }
  }

  it should "enforce unique source repositories" in {
    val ref1 = s"ref/${uniqueId}"
    val ref2 = s"ref/${uniqueId}"
    val ws1 =
      Workspace(uniqueId, repositories = Seq(SourceRepository(ref = ref1)))
    val ws2 =
      Workspace(uniqueId, repositories = Seq(SourceRepository(ref = ref2)))
    val ws3 =
      Workspace(uniqueId, repositories = Seq(SourceRepository(ref = ref1)))

    repo.store(ws1).unsafeRunSync()
    repo.store(ws2).unsafeRunSync() // ok different ref
    intercept[DataConflictException](repo.store(ws3).unsafeRunSync()) // not ok
  }
}
