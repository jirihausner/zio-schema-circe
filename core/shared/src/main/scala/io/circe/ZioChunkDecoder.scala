package io.circe

import zio.{Chunk, ChunkBuilder}

import scala.collection.mutable.Builder

object ZioChunkDecoder {

  implicit final def decodeChunk[A](implicit decodeA: Decoder[A]): Decoder[Chunk[A]] =
    new SeqDecoder[A, Chunk](decodeA) {
      final protected def createBuilder(): Builder[A, Chunk[A]] = ChunkBuilder.make[A]()
    }
}
