package spaces

import scalapb.TypeMapper

/** To increase type-safety in our implementation, we wrap all ids in a value class, and thus
  * make distinct types for them. Our goal is to prevent mixing up id types at compile time.
  * By creating a specific type of each id, a call to a function that accepts a WorkspaceId
  * will not compile if it is given an EnvironmentId.
  */
sealed trait IdType extends Any {
  def id: Long
}

// To make it a little more interesting, directory service Ids are strings and not longs...
sealed trait DirectoryServiceId extends Any {
  def id: String
}

object Ids {
  case class WorkspaceId(id: Long) extends AnyVal with IdType

  case class EnvironmentId(id: Long) extends AnyVal with IdType

  case class UserId(id: String) extends AnyVal with DirectoryServiceId

  case class UserGroupId(id: String) extends AnyVal with DirectoryServiceId

  // For ScalaPB, define bi-directional mappings from the wrapper types to the
  // underlying primitives.
  implicit val workspaceIdMapper = TypeMapper(WorkspaceId(_))(_.id)
  implicit val environmentIdMapper = TypeMapper(EnvironmentId(_))(_.id)

  implicit val userIdMapper = TypeMapper(UserId(_))(_.id)
  implicit val userGroupIdMapper = TypeMapper(UserGroupId(_))(_.id)
}
