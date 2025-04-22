package zio.schema.codec.circe

import io.circe._
import zio.schema._
import zio.schema.codec.circe.internal.{Codecs, JsonSplitter}
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk, ZIO}

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

object CirceCodec {

  @deprecated(
    """Use CirceCodec.Configuration instead.
 CirceCodec.Configuration allows configuring encoding/decoding of empty collection and nulls (options) independently.""",
    "0.3.2",
  )
  final case class Config(
    ignoreEmptyCollections: Boolean,
    ignoreNullValues: Boolean = true,
    treatStreamsAsArrays: Boolean = false,
  ) {
    private[circe] def toConfiguration: Configuration = Configuration(
      explicitEmptyCollections = ExplicitConfig(
        encoding = !ignoreEmptyCollections,
        decoding = !ignoreEmptyCollections,
      ),
      explicitNullValues = ExplicitConfig(
        encoding = !ignoreNullValues,
        decoding = !ignoreNullValues,
      ),
      treatStreamsAsArrays = treatStreamsAsArrays,
    )
  }

  object Config {

    @deprecated(
      """Use CirceCodec.Configuration.default instead.
 CirceCodec.Configuration allows configuring encoding/decoding of empty collection and nulls (options) independently.""",
      "0.3.2",
    )
    val default: Config = Config(ignoreEmptyCollections = false)
  }

  /**
   * When disabled for encoding, matching fields will be omitted from the JSON.
   * When disabled for decoding, missing fields will be decoded as default
   * value.
   */
  final case class ExplicitConfig(encoding: Boolean = true, decoding: Boolean = false)

  final case class Configuration(
    explicitEmptyCollections: ExplicitConfig = ExplicitConfig(),
    explicitNullValues: ExplicitConfig = ExplicitConfig(),
    treatStreamsAsArrays: Boolean = false,
  ) {
    def ignoreEmptyCollections: Configuration =
      copy(explicitEmptyCollections = ExplicitConfig(encoding = false, decoding = false))
    def ignoreNullValues: Configuration = copy(explicitNullValues = ExplicitConfig(encoding = false, decoding = false))
  }

  object Configuration {
    val default: Configuration = Configuration()
  }

  implicit def circeBinaryCodec[A](implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    circeBinaryCodec(Configuration.default)

  implicit def circeBinaryCodec[A](config: Configuration)(implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] =
        Chunk.fromArray(codec(value).noSpaces.getBytes(StandardCharsets.UTF_8))

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

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
        parser
          .decode[A](new String(whole.toArray, StandardCharsets.UTF_8))
          .left
          .map(failure => DecodeError.ReadError(Cause.fail(failure), failure.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapZIO { (str: String) =>
            ZIO
              .fromEither(parser.decode[A](str))
              .mapError(failure => DecodeError.ReadError(Cause.empty, failure.getMessage))
          }
    }

  @deprecated("Use Configuration based method instead", "0.3.2")
  def schemaBasedBinaryCodec[A](config: Config)(implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(config.toConfiguration)

  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] = CirceEncoder.encode(schema, value, config)

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

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
        CirceDecoder
          .decode(schema, new String(whole.toArray, StandardCharsets.UTF_8), config)
          .left
          .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapZIO { (json: String) =>
            ZIO.fromEither(
              CirceDecoder
                .decode(schema, json, config)
                .left
                .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage)),
            )
          }
    }

  @deprecated("Use Configuration based method instead", "0.3.2")
  def schemaEncoder[A](schema: Schema[A])(implicit config: Config = Config.default): Encoder[A] =
    Codecs.encodeSchema(schema, config.toConfiguration)

  def schemaEncoder[A](schema: Schema[A])(implicit config: Configuration): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceEncoder {

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

  object CirceDecoder {

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
