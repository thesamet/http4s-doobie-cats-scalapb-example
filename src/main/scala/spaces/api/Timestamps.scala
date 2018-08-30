package spaces.api

object Timestamps {
  def now = {
    val ms = System.currentTimeMillis()
    com.google.protobuf.timestamp.Timestamp(ms / 1000, 1000 * (ms % 1000).toInt)
  }
}
