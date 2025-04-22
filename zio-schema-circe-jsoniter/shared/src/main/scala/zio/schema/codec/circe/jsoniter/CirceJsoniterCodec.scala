package zio.schema.codec.circe.jsoniter

import io.circe._
import zio.schema.Schema
import zio.schema.codec.circe.CirceCodec.{Config, Configuration}
import zio.schema.codec.circe.internal.JsonSplitter
import zio.schema.codec.circe.jsoniter.internal.Codecs
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk, ZIO}

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

object CirceJsoniterCodec {

  @deprecated("Use Configuration based method instead", "0.3.2")
  def schemaBasedBinaryCodec[A](config: Config)(implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(config.toConfiguration)

  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

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
          ZPipeline.mapZIO { (json: String) =>
            ZIO.fromEither(
              CirceJsoniterDecoder
                .decode(schema, json, config)
                .left
                .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage)),
            )
          }

      override def encode(value: A): Chunk[Byte] =
        CirceJsoniterEncoder.encode(schema, value, config)

      override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
        if (config.treatStreamsAsArrays) {
          val interspersed: ZPipeline[Any, Nothing, A, Byte] = ZPipeline
            .mapChunks[A, Chunk[Byte]](_.map(encode))
            .intersperse(Chunk.single(','.toByte))
            .flattenChunks
          val prepended: ZPipeline[Any, Nothing, A, Byte]    =
            interspersed >>> ZPipeline.prepend(Chunk.single('['.toByte))
          prepended >>> ZPipeline.append(Chunk.single(']'.toByte))
        } else {
          ZPipeline.mapChunks[A, Chunk[Byte]](_.map(encode)).intersperse(Chunk.single('\n'.toByte)).flattenChunks
        }
    }

  @deprecated("Use Configuration based method instead", "0.3.2")
  def schemaEncoder[A](schema: Schema[A])(implicit config: Config = Config.default): Encoder[A] =
    Codecs.encodeSchema(schema, config.toConfiguration)

  def schemaEncoder[A](schema: Schema[A])(implicit config: Configuration): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceJsoniterEncoder {

    private[circe] def charSequenceToByteChunk(chars: CharSequence): Chunk[Byte] = {
      val bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
      Chunk.fromByteBuffer(bytes)
    }

    @deprecated("Use Configuration based method instead", "0.3.2")
    final def encode[A](schema: Schema[A], value: A, config: Config): Chunk[Byte] =
      encode(schema, value, config.toConfiguration)

    final def encode[A](schema: Schema[A], value: A, config: Configuration = Configuration.default): Chunk[Byte] =
      charSequenceToByteChunk(Codecs.encodeSchema(schema, config)(value).noSpaces)
  }

  def schemaDecoder[A](schema: Schema[A])(implicit config: Configuration = Configuration.default): Decoder[A] =
    Codecs.decodeSchema(schema, config)

  object CirceJsoniterDecoder {

    @deprecated("Use Configuration based method instead", "0.3.2")
    final def decode[A](schema: Schema[A], json: String): Either[Error, A] =
      decode(schema, json, Configuration.default)

    final def decode[A](
      schema: Schema[A],
      json: String,
      config: Configuration,
    ): Either[Error, A] = {
      implicit val decoder: Decoder[A] = Codecs.decodeSchema(schema, config)
      parser.decode[A](json)
    }
  }

  @deprecated("Use Configuration based method instead", "0.3.2")
  def schemaCodec[A](schema: Schema[A])(implicit config: Config = Config.default): Codec[A] = {
    val configuration: Configuration = config.toConfiguration
    Codec.from(Codecs.decodeSchema(schema, configuration), Codecs.encodeSchema(schema, configuration))
  }

  def schemaCodec[A](schema: Schema[A])(implicit config: Configuration): Codec[A] =
    Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
}
