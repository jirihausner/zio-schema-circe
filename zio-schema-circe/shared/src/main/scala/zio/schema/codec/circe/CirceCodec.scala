package zio.schema.codec.circe

import io.circe.{Codec, Decoder, Encoder, parser}
import zio.schema._
import zio.schema.codec.circe.internal._
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk}

object CirceCodec {

  object implicits {

    @inline
    implicit def circeBinaryCodec[A](implicit
      codec: Encoder[A] with Decoder[A],
      config: Configuration,
    ): BinaryCodec[A] =
      CirceCodec.circeBinaryCodec(config)

    @inline
    implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A], config: Configuration): BinaryCodec[A] =
      CirceCodec.schemaBasedBinaryCodec(config)

    @inline
    implicit def schemaCodec[A](implicit schema: Schema[A], config: Configuration): Codec[A] =
      CirceCodec.schemaCodec(config)
  }

  @inline
  def circeBinaryCodec[A](implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    circeBinaryCodec(Configuration.default)

  def circeBinaryCodec[A](config: Configuration)(implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] = charSequenceToByteChunk(codec(value).noSpaces)

      override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
        if (config.treatStreamsAsArrays) {
          val interspersed: ZPipeline[Any, Nothing, A, Byte] = ZPipeline
            .mapChunks[A, Chunk[Byte]](_.map(encode))
            .intersperse(JsonSplitter.jsonArraySeparator)
            .flattenChunks
          val prepended: ZPipeline[Any, Nothing, A, Byte]    =
            interspersed >>> ZPipeline.prepend(JsonSplitter.jsonArrayPrefix)
          prepended >>> ZPipeline.append(JsonSplitter.jsonArrayPostfix)
        } else {
          ZPipeline.mapChunks[A, Chunk[Byte]](_.map(encode)).intersperse(JsonSplitter.jsonNdSeparator).flattenChunks
        }

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
        parser
          .decode[A](byteChunkToString(whole))
          .left
          .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            parser.decode[A](json).left.map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
          }
    }

  @inline
  def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(Configuration.default)

  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      private implicit val codec: Codec[A] =
        Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))

      override def encode(value: A): Chunk[Byte] = charSequenceToByteChunk(codec(value).noSpaces)

      override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
        if (config.treatStreamsAsArrays) {
          val interspersed: ZPipeline[Any, Nothing, A, Byte] = ZPipeline
            .mapChunks[A, Chunk[Byte]](_.map(encode))
            .intersperse(JsonSplitter.jsonArraySeparator)
            .flattenChunks
          val prepended: ZPipeline[Any, Nothing, A, Byte]    =
            interspersed >>> ZPipeline.prepend(JsonSplitter.jsonArrayPrefix)
          prepended >>> ZPipeline.append(JsonSplitter.jsonArrayPostfix)
        } else {
          ZPipeline.mapChunks[A, Chunk[Byte]](_.map(encode)).intersperse(JsonSplitter.jsonNdSeparator).flattenChunks
        }

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
        parser
          .decode[A](byteChunkToString(whole))
          .left
          .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            parser.decode[A](json).left.map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
          }
    }

  @inline
  def schemaEncoder[A](implicit schema: Schema[A]): Encoder[A] = schemaEncoder(Configuration.default)

  def schemaEncoder[A](config: Configuration)(implicit schema: Schema[A]): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  @inline
  def schemaDecoder[A](implicit schema: Schema[A]): Decoder[A] = schemaDecoder(Configuration.default)

  def schemaDecoder[A](config: Configuration)(implicit schema: Schema[A]): Decoder[A] =
    Codecs.decodeSchema(schema, config)

  @inline
  def schemaCodec[A](implicit schema: Schema[A]): Codec[A] = schemaCodec(Configuration.default)

  def schemaCodec[A](config: Configuration)(implicit schema: Schema[A]): Codec[A] =
    Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
}
