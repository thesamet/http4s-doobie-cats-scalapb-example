package spaces.api

import spaces.Id
import spaces.api.protos.{Environment, Workspace}

import scala.util.Try

class IdVar[A] {
  def unapply(arg: String): Option[Id[A]] =
    if (arg.isEmpty) None
    else Try(Id[A](arg.toLong)).toOption
}

object WorkspaceIdVar extends IdVar[Workspace]

object EnvironmentIdVar extends IdVar[Environment]
