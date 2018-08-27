package spaces.api

import spaces.IdType
import spaces.Ids.{EnvironmentId, WorkspaceId}

import scala.util.Try

class IdPathVar[A <: IdType](cast: Int => A) {
  def unapply(arg: String): Option[A] =
    if (arg.isEmpty) None
    else Try(cast(arg.toInt)).toOption
}

object WorkspaceIdVar extends IdPathVar(WorkspaceId(_))

object EnvironmentIdVar extends IdPathVar(EnvironmentId(_))
