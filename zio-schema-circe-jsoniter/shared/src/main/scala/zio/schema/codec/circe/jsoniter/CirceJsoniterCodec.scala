package zio.schema.codec.circe.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReaderException, readFromArray, readFromString, writeToArray}
import io.circe._
import zio.schema.codec.circe.internal.{Configuration => InternalConfiguration, ErrorHandler, JsonSplitter}
import zio.schema.codec.circe.jsoniter.internal.Codecs
import zio.schema.codec.circe.{DiscriminatorSetting, ExplicitConfig}
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.schema.{NameFormat, Schema}
import zio.stream.ZPipeline
import zio.{Cause, Chunk}

import java.nio.charset.StandardCharsets

object CirceJsoniterCodec {

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
      CirceJsoniterCodec.schemaCodec(config)(schema)
  }

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

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] = {
        try decoder(readFromArray(whole.toArray)(jsonC3c).hcursor)
        catch { case jre: JsonReaderException => Left(ParsingFailure(jre.getMessage, jre)) }
      }.left.map(ErrorHandler.handle)

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            try decoder(readFromString(json)(jsonC3c).hcursor)
            catch { case jre: JsonReaderException => Left(ParsingFailure(jre.getMessage, jre)) }
          }
            .mapError(ErrorHandler.handle)
    }

  @inline
  def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A] =
    schemaBasedBinaryCodec(Configuration.default)

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
          .map(ErrorHandler.handle)

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
          (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
           else JsonSplitter.splitOnJsonBoundary) >>>
          ZPipeline.mapEitherChunked { (json: String) =>
            CirceJsoniterDecoder
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

  object CirceJsoniterEncoder {

    final def encode[A](schema: Schema[A], value: A, config: Configuration = Configuration.default): Chunk[Byte] =
      Chunk.fromArray(writeToArray(Codecs.encodeSchema(schema, config)(value))(jsonC3c))
  }

  @inline
  def schemaDecoder[A](schema: Schema[A]): Decoder[A] = schemaDecoder(Configuration.default)(schema)

  @inline
  def schemaDecoder[A](config: Configuration)(schema: Schema[A]): Decoder[A] =
    Codecs.decodeSchema(schema, config)

  object CirceJsoniterDecoder {

    final def decode[A](
      schema: Schema[A],
      json: String,
      config: Configuration = Configuration.default,
    ): Either[Error, A] = {
      implicit val decoder: Decoder[A] = Codecs.decodeSchema(schema, config)
      try decoder(readFromString(json)(jsonC3c).hcursor)
      catch { case jre: JsonReaderException => Left(ParsingFailure(jre.getMessage, jre)) }
    }
  }

  @inline
  def schemaCodec[A](schema: Schema[A]): Codec[A] = schemaCodec(Configuration.default)(schema)

  @inline
  def schemaCodec[A](config: Configuration)(schema: Schema[A]): Codec[A] =
    Codec.from(Codecs.decodeSchema(schema, config), Codecs.encodeSchema(schema, config))
}
