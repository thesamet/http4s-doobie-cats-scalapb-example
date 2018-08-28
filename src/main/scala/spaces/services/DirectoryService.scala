package spaces.services

import cats.effect.IO
import spaces.Ids._
import spaces.auth.{User, UserGroup}

/** Represents a remote user authentication/lookup service */
trait DirectoryService {

  /** Returns a user that corresponds to the API token */
  def fromApiToken(token: String): IO[Option[User]]

  /** Looks up a group with the matching group id */
  def getGroup(groupId: UserGroupId): IO[Option[UserGroup]]
}

/** Implementation that serves static data */
object StaticDirectoryService extends DirectoryService {
  val user1 = User(UserId("user1"),
                   "User 1",
                   "user1@email.com",
                   Seq(UserGroupId("g1"), UserGroupId("g2")))
  val user2 = User(UserId("user2"),
                   "User 2",
                   "user2@email.com",
                   Seq(UserGroupId("g2"), UserGroupId("g3")))
  val user3 = User(UserId("user3"), "User 3", "user3@email.com", Seq())

  override def fromApiToken(token: String): IO[Option[User]] = IO.pure {
    token match {
      case "Bearer t1" => Some(user1)
      case "Bearer t2" => Some(user2)
      case "Bearer t3" => Some(user3)
      case _           => None
    }
  }

  def getGroup(groupRef: UserGroupId): IO[Option[UserGroup]] = IO.pure {
    groupRef match {
      case UserGroupId("g1") =>
        Some(UserGroup(UserGroupId("g1"), "Group 1", Seq(user1)))
      case UserGroupId("g2") =>
        Some(UserGroup(UserGroupId("g2"), "Group 2", Seq(user1, user2)))
      case UserGroupId("g3") =>
        Some(UserGroup(UserGroupId("g3"), "Group 3", Seq(user2)))
      case _ => None
    }
  }
}
