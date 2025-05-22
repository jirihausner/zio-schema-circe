package zio.schema.codec.circe

import zio.Chunk

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

package object internal {

  @inline
  private[circe] def charSequenceToByteChunk(chars: CharSequence): Chunk[Byte] = {
    val bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
    Chunk.fromByteBuffer(bytes)
  }

  @inline
  private[circe] def byteChunkToString(bytes: Chunk[Byte]): String = {
    new String(bytes.toArray, StandardCharsets.UTF_8)
  }
}
