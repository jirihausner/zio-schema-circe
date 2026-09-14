package zio.schema.codec.circe

import io.circe._
import zio.schema._
import zio.schema.codec.circe.internal.{Codecs, Configuration => InternalConfiguration, ErrorHandler, JsonSplitter}
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk}

import java.nio.charset.StandardCharsets

object CirceCodec {

  /**
   * Configuration for the JSON codec. The configurations are overruled by the
   * annotations that configure the same behavior.
   *
   * @param explicitEmptyCollections
   *   whether to encode empty collections as `[]` or omit the field and decode
   *   the field when it is missing as an empty collection or fail
   * @param explicitNulls
   *   whether to encode empty Options as `null` or omit the field and decode
   *   the field when it is missing to None or fail
   * @param discriminatorSettings
   *   set up how to handle discriminators
   * @param fieldNameFormat
   *   format for the field names
   * @param treatStreamsAsArrays
   *   whether to treat streams as arrays when encoding/decoding
   * @param rejectExtraFields
   *   whether to reject extra fields during decoding
   */
  final case class Configuration(
    explicitEmptyCollections: ExplicitConfig = ExplicitConfig(),
    explicitNullValues: ExplicitConfig = ExplicitConfig(),
    discriminatorSettings: DiscriminatorSetting = DiscriminatorSetting.default,
    fieldNameFormat: NameFormat = NameFormat.Identity,
    treatStreamsAsArrays: Boolean = false,
    rejectExtraFields: Boolean = false,
  ) extends InternalConfiguration {
    def withEmptyCollectionsIgnored: Configuration =
      copy(explicitEmptyCollections = ExplicitConfig(encoding = false, decoding = false))

    def withNullValuesIgnored: Configuration =
      copy(explicitNullValues = ExplicitConfig(encoding = false, decoding = false))

    def withNoDiscriminator: Configuration = copy(discriminatorSettings = DiscriminatorSetting.NoDiscriminator)

    def withDiscriminator(format: NameFormat): Configuration =
      copy(discriminatorSettings = DiscriminatorSetting.ClassName(format))

    def withDiscriminator(name: String, format: NameFormat = NameFormat.Identity): Configuration =
      copy(discriminatorSettings = DiscriminatorSetting.Name(name, format))

    def withFieldFormat(format: NameFormat): Configuration = copy(fieldNameFormat = format)

    def withStreamsTreatedAsArrays: Configuration = copy(treatStreamsAsArrays = true)

    def withExtraFieldsSkipped: Configuration = copy(rejectExtraFields = false)

    def withExtraFieldsRejected: Configuration = copy(rejectExtraFields = true)
  }

  object Configuration {
    val default: Configuration = Configuration()
  }

  object implicits {

    @inline
    implicit def circeBinaryCodec[A](implicit
      decoder: Decoder[A],
      encoder: Encoder[A],
      config: Configuration,
    ): BinaryCodec[A] = CirceCodec.circeBinaryCodec(config)

    @inline
    implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A], config: Configuration): BinaryCodec[A] =
      CirceCodec.schemaBasedBinaryCodec(config)

    @inline
    implicit def schemaCodec[A](implicit schema: Schema[A], config: Configuration): Codec[A] =
      CirceCodec.schemaCodec(config)(schema)
  }

  @inline
  def circeBinaryCodec[A](implicit encoder: Encoder[A], decoder: Decoder[A]): BinaryCodec[A] =
    circeBinaryCodec(Configuration.default)

  def circeBinaryCodec[A](config: Configuration)(implicit encoder: Encoder[A], decoder: Decoder[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] =
        Chunk.fromArray(encoder(value).noSpaces.getBytes(StandardCharsets.UTF_8))

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
          .decode[A](new String(whole.toArray, StandardCharsets.UTF_8))
          .left
          .map(ErrorHandler.handle)

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            parser.decode[A](json).left.map(ErrorHandler.handle)
          }
    }

  @inline
  def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(Configuration.default)

  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] = CirceEncoder.encode(schema, value, config)

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
        CirceDecoder
          .decode(schema, new String(whole.toArray, StandardCharsets.UTF_8), config)
          .left
          .map(ErrorHandler.handle)

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            CirceDecoder
              .decode(schema, json, config)
              .left
              .map(ErrorHandler.handle)
          }
    }

  @inline
  def schemaEncoder[A](schema: Schema[A]): Encoder[A] = schemaEncoder(Configuration.default)(schema)

  @inline
  def schemaEncoder[A](config: Configuration)(schema: Schema[A]): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceEncoder {

    import zio.schema.codec.circe.internal.charSequenceToByteChunk

    final def encode[A](schema: Schema[A], value: A, config: Configuration = Configuration.default): Chunk[Byte] =
      charSequenceToByteChunk(Codecs.encodeSchema(schema, config)(value).noSpaces)
  }

  @inline
  def schemaDecoder[A](schema: Schema[A]): Decoder[A] = schemaDecoder(Configuration.default)(schema)

  @inline
  def schemaDecoder[A](config: Configuration)(schema: Schema[A]): Decoder[A] =
    Codecs.decodeSchema(schema, config)

  object CirceDecoder {

    final def decode[A](
      schema: Schema[A],
      json: String,
      config: Configuration = Configuration.default,
    ): Either[Error, A] = {
      implicit val decoder: Decoder[A] = Codecs.decodeSchema(schema, config)
      parser.decode[A](json)
    }
  }

  @inline
  def schemaCodec[A](schema: Schema[A]): Codec[A] = schemaCodec(Configuration.default)(schema)

  @inline
  def schemaCodec[A](config: Configuration)(schema: Schema[A]): Codec[A] =
    Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
}
