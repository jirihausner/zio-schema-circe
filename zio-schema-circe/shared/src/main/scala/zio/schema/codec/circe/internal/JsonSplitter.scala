package zio.schema.codec.circe.internal

import zio.stream.{ZChannel, ZPipeline}
import zio.{Chunk, ChunkBuilder, ZNothing}

/**
 * Adapted from zio-schema-json (https://github.com/zio/zio-schema).
 *
 * Credits to zio-schema-json contributors for initial implementation.
 */
private[circe] object JsonSplitter {

  val validNumChars: Set[Char] = Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'E', 'e', '-', '+', '.')

  val ContextJson            = 'j'
  val ContextString          = 's'
  val ContextBoolean         = 'b'
  val ContextNull            = 'u'
  val ContextNullAfterFirstL = 'x'
  val ContextNumber          = 'n'
  val ContextEscape          = 'e'
  val ContextDone            = 'd'

  def jsonSplitter(wrappedInArray: Boolean): ZPipeline[Any, Nothing, String, String] = {
    ZPipeline.suspend {
      val stringBuilder = new StringBuilder
      var depth         = if (wrappedInArray) -1 else 0
      var context       = ContextJson

      def fetchChunk(chunk: Chunk[String]): Chunk[String] = {
        val chunkBuilder = ChunkBuilder.make[String]()
        for {
          string <- chunk
          c      <- string
        } {
          var valueEnded = false
          context match {
            case ContextEscape          =>
              context = ContextString
            case ContextString          =>
              c match {
                case '\\' => context = ContextEscape
                case '"'  =>
                  context = ContextJson
                  valueEnded = true
                case _    =>
              }
            case ContextBoolean         =>
              if (c == 'e') {
                context = ContextJson
                valueEnded = true
              }
            case ContextNull            =>
              if (c == 'l') {
                context = ContextNullAfterFirstL
              }
            case ContextNullAfterFirstL =>
              if (c == 'l') {
                context = ContextJson
                valueEnded = true
              }
            case ContextNumber          =>
              c match {
                case '}' | ']'              =>
                  depth -= 1
                  context = if (depth < 0) ContextDone else ContextJson
                  valueEnded = true
                case _ if !validNumChars(c) =>
                  context = ContextJson
                  valueEnded = true
                case _                      =>
              }
            case ContextDone            => // no more values, ignore everything
            case _                      =>
              c match {
                case '{' | '['             =>
                  depth += 1
                case '}' | ']'             =>
                  depth -= 1
                  valueEnded = true
                  if (depth == -1) context = ContextDone
                case '"'                   =>
                  context = ContextString
                case 't' | 'f'             =>
                  context = ContextBoolean
                case 'n'                   =>
                  context = ContextNull
                case x if validNumChars(x) =>
                  context = ContextNumber
                case _                     =>
              }
          }
          if (context != ContextDone && (depth > 0 || context != ContextJson || valueEnded))
            stringBuilder.append(c)

          if (valueEnded && depth == 0) {
            val str = stringBuilder.result()
            if (!str.forall(_.isWhitespace)) {
              chunkBuilder += str
            }
            stringBuilder.clear()
          }
        }
        chunkBuilder.result()
      }

      lazy val loop: ZChannel[Any, ZNothing, Chunk[String], Any, Nothing, Chunk[String], Any] =
        ZChannel.readWithCause(
          in => {
            val out = fetchChunk(in)
            if (out.isEmpty) loop else ZChannel.write(out) *> loop
          },
          err =>
            if (stringBuilder.isEmpty) ZChannel.refailCause(err)
            else ZChannel.write(Chunk.single(stringBuilder.result())) *> ZChannel.refailCause(err),
          done =>
            if (stringBuilder.isEmpty) ZChannel.succeed(done)
            else ZChannel.write(Chunk.single(stringBuilder.result())) *> ZChannel.succeed(done),
        )

      ZPipeline.fromChannel(loop)
    }
  }

  val splitOnJsonBoundary: ZPipeline[Any, Nothing, String, String] = JsonSplitter.jsonSplitter(wrappedInArray = false)
  val splitJsonArrayElements: ZPipeline[Any, Nothing, String, String] = JsonSplitter.jsonSplitter(wrappedInArray = true)

  val jsonNdSeparator: Chunk[Byte]    = Chunk.single('\n'.toByte)
  val jsonArraySeparator: Chunk[Byte] = Chunk.single(','.toByte)
  val jsonArrayPrefix: Chunk[Byte]    = Chunk.single('['.toByte)
  val jsonArrayPostfix: Chunk[Byte]   = Chunk.single(']'.toByte)
}
