package spaces.api

import cats.effect.IO
import io.circe.{Json, Printer}
import org.http4s.circe.CirceInstances
import org.http4s.{DecodeResult, EntityDecoder, InvalidMessageBodyFailure}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

trait JsonProtoUtils {
  val circeInstances = CirceInstances.withPrinter(Printer.spaces2)
  import circeInstances._

  implicit def jsonOf[
      T <: GeneratedMessage with Message[T]: GeneratedMessageCompanion]
    : EntityDecoder[IO, T] =
    jsonDecoder[IO].flatMapR { json =>
      scalapb_circe.JsonFormat
        .protoToDecoder[T]
        .decodeJson(json)
        .fold(
          failure =>
            DecodeResult.failure(
              InvalidMessageBodyFailure(s"Could not decode JSON: $json",
                                        Some(failure))),
          DecodeResult.success(_)
        )
    }

  implicit def entityEncoder[
      T <: GeneratedMessage with Message[T]: GeneratedMessageCompanion] = {
    jsonEncoderWithPrinterOf[IO, Json](Printer.spaces2)
      .contramap(scalapb_circe.JsonFormat.toJson[T])
  }
}

object JsonProtoUtils extends JsonProtoUtils
