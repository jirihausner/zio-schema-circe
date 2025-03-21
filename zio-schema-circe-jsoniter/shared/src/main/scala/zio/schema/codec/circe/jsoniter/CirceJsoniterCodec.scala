package zio.schema.codec.circe.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, readFromString, writeToArray}
import io.circe._
import zio.schema.Schema
import zio.schema.codec.circe.CirceCodec.Config
import zio.schema.codec.circe.internal.JsonSplitter
import zio.schema.codec.circe.jsoniter.internal.Codecs
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline
import zio.{Cause, Chunk, ZIO}

import java.nio.charset.StandardCharsets

object CirceJsoniterCodec {

  implicit def circeJsoniterBinaryCodec[A](implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] = Chunk.fromArray(writeToArray(codec(value))(jsonC3c))

      override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
        ZPipeline.mapChunks[A, Chunk[Byte]](_.map(encode)).intersperse(Chunk.single('\n'.toByte)).flattenChunks

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
        codec(readFromArray(whole.toArray)(jsonC3c).hcursor).left
          .map(failure => DecodeError.ReadError(Cause.fail(failure), failure.getMessage))

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.fromChannel {
          ZPipeline.utf8Decode.channel.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage))
        } >>> JsonSplitter.splitOnJsonBoundary >>> ZPipeline.mapZIO { (str: String) =>
          ZIO
            .fromEither(parser.decode[A](str))
            .mapError(failure => DecodeError.ReadError(Cause.empty, failure.getMessage))
        }
    }

  def schemaBasedBinaryCodec[A](config: Config)(implicit schema: Schema[A]): BinaryCodec[A] = new BinaryCodec[A] {

    override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
      CirceJsoniterDecoder
        .decode(
          schema,
          new String(whole.toArray, StandardCharsets.UTF_8),
        )
        .left
        .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
      ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
        (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
         else JsonSplitter.splitOnJsonBoundary) >>>
        ZPipeline.mapZIO { (s: String) =>
          ZIO.fromEither(
            CirceJsoniterDecoder.decode(schema, s).left.map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage)),
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

  def schemaEncoder[A](schema: Schema[A])(implicit config: Config = Config.default): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceJsoniterEncoder {

    final def encode[A](schema: Schema[A], value: A, cfg: Config): Chunk[Byte] =
      Chunk.fromArray(writeToArray(Codecs.encodeSchema(schema, cfg)(value))(jsonC3c))
  }

  def schemaDecoder[A](schema: Schema[A]): Decoder[A] =
    Codecs.decodeSchema(schema)

  object CirceJsoniterDecoder {

    final def decode[A](schema: Schema[A], json: String): Either[Error, A] = {
      implicit val decoder: Decoder[A] = Codecs.decodeSchema(schema)
      decoder(readFromString(json)(jsonC3c).hcursor)
    }
  }

  def schemaCodec[A](schema: Schema[A]): Codec[A] =
    Codec.from(schemaDecoder(schema), schemaEncoder(schema))
}
