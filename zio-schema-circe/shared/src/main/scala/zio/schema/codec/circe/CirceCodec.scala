package zio.schema.codec.circe

import io.circe._
import zio.schema._
import zio.schema.codec.circe.internal.{Codecs, Configuration => InternalConfiguration, JsonSplitter}
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk}

import java.nio.charset.StandardCharsets

object CirceCodec {

  @deprecated(
    """Use CirceCodec.Configuration instead.
 CirceCodec.Configuration allows configuring encoding/decoding of empty collection and nulls independently.""",
    "0.4.0",
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
 CirceCodec.Configuration allows configuring encoding/decoding of empty collection and nulls independently.""",
      "0.4.0",
    )
    val default: Config = Config(ignoreEmptyCollections = false)
  }

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

  @deprecated("Use CirceCodec.implicits.circeBinaryCodec instead", "0.4.0")
  implicit def circeBinaryCodec[A](codec: Encoder[A] with Decoder[A]): BinaryCodec[A] = {
    implicit val encoder: Encoder[A] = codec
    implicit val decoder: Decoder[A] = codec
    circeBinaryCodec(Configuration.default)
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
          .map(failure => DecodeError.ReadError(Cause.fail(failure), failure.getMessage))

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

  @deprecated("Use Configuration based method instead", "0.4.0")
  def schemaBasedBinaryCodec[A](config: Config)(implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(config.toConfiguration)

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
          .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            CirceDecoder
              .decode(schema, json, config)
              .left
              .map(error => DecodeError.ReadError(Cause.fail(error), error.getMessage))
          }
    }

  @deprecated("Use Configuration based method instead", "0.4.0")
  def schemaEncoder[A](schema: Schema[A])(implicit config: Config = Config.default): Encoder[A] =
    Codecs.encodeSchema(schema, config.toConfiguration)

  @inline
  def schemaEncoder[A](config: Configuration)(schema: Schema[A]): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceEncoder {

    import zio.schema.codec.circe.internal.charSequenceToByteChunk

    @deprecated("Use Configuration based method instead", "0.4.0")
    final def encode[A](schema: Schema[A], value: A, config: Config): Chunk[Byte] =
      encode(schema, value, config.toConfiguration)

    final def encode[A](schema: Schema[A], value: A, config: Configuration = Configuration.default): Chunk[Byte] =
      charSequenceToByteChunk(Codecs.encodeSchema(schema, config)(value).noSpaces)
  }

  @inline
  def schemaDecoder[A](schema: Schema[A]): Decoder[A] = schemaDecoder(Configuration.default)(schema)

  @inline
  def schemaDecoder[A](config: Configuration)(schema: Schema[A]): Decoder[A] =
    Codecs.decodeSchema(schema, config)

  object CirceDecoder {

    @deprecated("Use Configuration based method instead", "0.4.0")
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

  @deprecated("Use Configuration based method instead", "0.4.0")
  def schemaCodec[A](schema: Schema[A])(implicit config: Config = Config.default): Codec[A] = {
    val configuration: Configuration = config.toConfiguration
    Codec.from(Codecs.decodeSchema(schema, configuration), Codecs.encodeSchema(schema, configuration))
  }

  @inline
  def schemaCodec[A](config: Configuration)(schema: Schema[A]): Codec[A] =
    Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
}
