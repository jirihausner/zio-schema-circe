package zio.schema.codec.circe.internal

import io.circe.{CursorOp, DecodingFailure, Error, ParsingFailure}
import zio.schema.codec.DecodeError
import zio.{Cause, Chunk, ChunkBuilder}

private[circe] object ErrorHandler {

  def handle(error: Error): DecodeError = error match {
    case e: ParsingFailure  => DecodeError.ReadError(Cause.fail(e.underlying), e.message)
    case e: DecodingFailure =>
      historyToPathChunk(e.history) match {
        case path if path.nonEmpty => DecodeError.ReadErrorWithPath(path, Cause.fail(error), e.message)
        case _                     => DecodeError.ReadError(Cause.fail(error), e.message)
      }
  }

  private sealed trait Selection
  private object Selection {
    case class Field(field: String) extends Selection
    case class Index(index: Int)    extends Selection
  }

  private def historyToPathChunk(history: List[CursorOp]): Chunk[String] = {
    import io.circe.CursorOp._

    if (history.isEmpty) Chunk.empty
    else {
      val selections =
        try
          history.foldRight(List.empty[Selection]) {
            case (MoveLeft, Selection.Index(i) :: tail) if i > 0             =>
              Selection.Index(i - 1) :: tail
            case (MoveRight, Selection.Index(i) :: tail) if i < Int.MaxValue =>
              Selection.Index(i + 1) :: tail
            case (MoveUp, _ :: tail)                                         => tail
            case (Field(k), Selection.Field(_) :: tail)                      => Selection.Field(k) :: tail
            case (DownField(k), acc)                                         => Selection.Field(k) :: acc
            case (DownArray, acc)                                            => Selection.Index(0) :: acc
            case (DownN(n), acc)                                             => Selection.Index(n) :: acc
            case (DeleteGoParent, _ :: tail)                                 => tail
            case _                                                           =>
              throw new IllegalStateException("Unexpected cursor operation")
          }
        catch {
          case _: IllegalStateException => Nil // in case of any error we return empty path
        }
      if (selections.isEmpty) return Chunk.empty
      val (cb, sb)   = selections.foldRight(
        (
          ChunkBuilder.make[String](),
          new StringBuilder(),
        ),
      ) {
        case (Selection.Field(key), (cb, sb))   =>
          if (sb.nonEmpty) {
            cb += sb.result()
            sb.clear()
          }
          (cb, sb.append(key))
        case (Selection.Index(index), (cb, sb)) =>
          (cb, sb.append("[").append(index.toString).append("]"))
      }
      if (sb.nonEmpty) cb += sb.result()
      cb.result()
    }
  }
}
