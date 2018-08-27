package spaces.services

sealed trait ApiError

case class DataConflictException(msg: String)
    extends Exception(msg)
    with ApiError

case class DuplicateName(msg: String) extends Exception(msg) with ApiError

case class ObjectNotFound(msg: String) extends Exception(msg) with ApiError

case class InvalidRequest(msg: String) extends Exception(msg) with ApiError

case class NoAccess(msg: String) extends Exception(msg) with ApiError
