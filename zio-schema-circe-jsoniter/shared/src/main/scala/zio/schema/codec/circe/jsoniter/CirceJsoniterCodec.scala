package zio.schema.codec.circe.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, readFromString, writeToArray}
import io.circe._
import zio.schema.Schema
import zio.schema.codec.circe.CirceCodec.{Config, Configuration}
import zio.schema.codec.circe.internal.JsonSplitter
import zio.schema.codec.circe.jsoniter.internal.Codecs
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk}

import java.nio.charset.StandardCharsets

object CirceJsoniterCodec {

  object implicits {

    @inline
    implicit def circeJsoniterBinaryCodec[A](implicit
      decoder: Decoder[A],
      encoder: Encoder[A],
      config: Configuration,
    ): BinaryCodec[A] = CirceJsoniterCodec.circeJsoniterBinaryCodec(config)

    @inline
    implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A], config: Configuration): BinaryCodec[A] =
      CirceJsoniterCodec.schemaBasedBinaryCodec(config)

    @inline
    implicit def schemaCodec[A](implicit schema: Schema[A], config: Configuration): Codec[A] =
      Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
  }

  @deprecated("Use CirceJsoniterCodec.implicits.circeJsoniterBinaryCodec instead", "0.4.0")
  implicit def circeJsoniterBinaryCodec[A](implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    circeJsoniterBinaryCodec(Configuration.default)

  @inline
  def circeJsoniterBinaryCodec[A](implicit encoder: Encoder[A], decoder: Decoder[A]): BinaryCodec[A] =
    circeJsoniterBinaryCodec(Configuration.default)

  def circeJsoniterBinaryCodec[A](
    config: Configuration,
  )(implicit encoder: Encoder[A], decoder: Decoder[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] = Chunk.fromArray(writeToArray(encoder(value))(jsonC3c))

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
        decoder(readFromArray(whole.toArray)(jsonC3c).hcursor).left
          .map(failure => DecodeError.ReadError(Cause.fail(failure), failure.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            decoder(readFromString(json)(jsonC3c).hcursor).left
              .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
          }
    }

  @deprecated("Use Configuration based method instead", "0.4.0")
  def schemaBasedBinaryCodec[A](config: Config)(implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(config.toConfiguration)

  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] =
        CirceJsoniterEncoder.encode(schema, value, config)

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
        CirceJsoniterDecoder
          .decode(
            schema,
            new String(whole.toArray, StandardCharsets.UTF_8),
            config,
          )
          .left
          .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            CirceJsoniterDecoder
              .decode(schema, json, config)
              .left
              .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
          }
    }

  @deprecated("Use Configuration based method instead", "0.4.0")
  def schemaEncoder[A](schema: Schema[A])(implicit config: Config = Config.default): Encoder[A] =
    Codecs.encodeSchema(schema, config.toConfiguration)

  @inline
  def schemaEncoder[A](schema: Schema[A])(implicit config: Configuration): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceJsoniterEncoder {

    @deprecated("Use Configuration based method instead", "0.4.0")
    final def encode[A](schema: Schema[A], value: A, config: Config): Chunk[Byte] =
      encode(schema, value, config.toConfiguration)

    final def encode[A](schema: Schema[A], value: A, config: Configuration = Configuration.default): Chunk[Byte] =
      Chunk.fromArray(writeToArray(Codecs.encodeSchema(schema, config)(value))(jsonC3c))
  }

  @inline
  def schemaDecoder[A](schema: Schema[A])(implicit config: Configuration = Configuration.default): Decoder[A] =
    Codecs.decodeSchema(schema, config)

  object CirceJsoniterDecoder {

    @deprecated("Use Configuration based method instead", "0.4.0")
    final def decode[A](schema: Schema[A], json: String): Either[Error, A] =
      decode(schema, json, Configuration.default)

    final def decode[A](
      schema: Schema[A],
      json: String,
      config: Configuration,
    ): Either[Error, A] = {
      implicit val decoder: Decoder[A] = Codecs.decodeSchema(schema, config)
      decoder(readFromString(json)(jsonC3c).hcursor)
      parser.decode[A](json)
    }
  }

  @deprecated("Use Configuration based method instead", "0.4.0")
  def schemaCodec[A](schema: Schema[A])(implicit config: Config = Config.default): Codec[A] = {
    val configuration: Configuration = config.toConfiguration
    Codec.from(Codecs.decodeSchema(schema, configuration), Codecs.encodeSchema(schema, configuration))
  }

  @inline
  def schemaCodec[A](schema: Schema[A])(implicit config: Configuration): Codec[A] =
    Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
}
