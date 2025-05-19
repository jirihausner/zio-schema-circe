package zio.schema.codec.circe.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, readFromString, writeToArray}
import io.circe._
import zio.schema.Schema
import zio.schema.codec.circe.Configuration
import zio.schema.codec.circe.internal.JsonSplitter
import zio.schema.codec.circe.jsoniter.internal.Codecs
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk}

object CirceJsoniterCodec {

  object implicits {

    @inline
    implicit def circeJsoniterBinaryCodec[A](implicit
      codec: Encoder[A] with Decoder[A],
      config: Configuration,
    ): BinaryCodec[A] =
      CirceJsoniterCodec.circeJsoniterBinaryCodec(config)

    @inline
    implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A], config: Configuration): BinaryCodec[A] =
      CirceJsoniterCodec.schemaBasedBinaryCodec(config)

    @inline
    implicit def schemaCodec[A](implicit schema: Schema[A], config: Configuration): Codec[A] =
      CirceJsoniterCodec.schemaCodec(config)
  }

  @inline
  implicit def circeJsoniterBinaryCodec[A](implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    circeJsoniterBinaryCodec(Configuration.default)

  implicit def circeJsoniterBinaryCodec[A](
    config: Configuration,
  )(implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] = Chunk.fromArray(writeToArray(codec(value))(jsonC3c))

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
        codec(readFromArray(whole.toArray)(jsonC3c).hcursor).left
          .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            codec(readFromString(json)(jsonC3c).hcursor).left
              .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
          }
    }

  @inline
  def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(Configuration.default)

  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      private implicit val codec: Codec[A] =
        Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))

      override def encode(value: A): Chunk[Byte] = Chunk.fromArray(writeToArray(codec(value))(jsonC3c))

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
        codec(readFromArray(whole.toArray)(jsonC3c).hcursor).left
          .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            codec(readFromString(json)(jsonC3c).hcursor).left
              .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
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
