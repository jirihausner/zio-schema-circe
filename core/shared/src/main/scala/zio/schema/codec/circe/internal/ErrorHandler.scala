package zio.schema.codec.circe.internal

import io.circe.{DecodingFailure, Error, ParsingFailure}
import zio.schema.codec.DecodeError
import zio.{Cause, Chunk}

private[circe] object ErrorHandler {

  def handle(error: Error): DecodeError = error match {
    case e: ParsingFailure  => DecodeError.ReadError(Cause.fail(e.underlying), e.message)
    case e: DecodingFailure =>
      e.pathToRootString match {
        case Some(path) if path.nonEmpty =>
          DecodeError.ReadErrorWithPath(pathToChunk(path), Cause.fail(error), e.message)
        case _                           =>
          DecodeError.ReadError(Cause.fail(error), e.message)
      }
  }

  private def pathToChunk(path: String): Chunk[String] = {
    val parts = path.split('.').toList.filter(_.nonEmpty)
    Chunk.fromIterable(parts)
  }
}
