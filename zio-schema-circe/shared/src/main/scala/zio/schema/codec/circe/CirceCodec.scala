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

  final case class Config(
    ignoreEmptyCollections: Boolean,
    ignoreNullValues: Boolean = true,
    treatStreamsAsArrays: Boolean = false,
  )

  object Config {
    val default: Config = Config(ignoreEmptyCollections = false)
  }

  implicit def circeBinaryCodec[A](implicit codec: Encoder[A] with Decoder[A]): BinaryCodec[A] = new BinaryCodec[A] {

    override def encode(value: A): Chunk[Byte] = Chunk.fromArray(codec(value).noSpaces.getBytes(StandardCharsets.UTF_8))

    override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
      ZPipeline.mapChunks[A, Chunk[Byte]](_.map(encode)).intersperse(Chunk.single('\n'.toByte)).flattenChunks

    override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
      parser
        .decode[A](new String(whole.toArray, StandardCharsets.UTF_8))
        .left
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
        .decode(schema, new String(whole.toArray, StandardCharsets.UTF_8))
        .left
        .map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
      ZPipeline.utfDecode.mapError(cce => DecodeError.ReadError(Cause.fail(cce), cce.getMessage)) >>>
        (if (config.treatStreamsAsArrays) JsonSplitter.splitJsonArrayElements
         else JsonSplitter.splitOnJsonBoundary) >>>
        ZPipeline.mapZIO { (json: String) =>
          ZIO.fromEither(
            CirceDecoder.decode(schema, json).left.map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage)),
          )
        }
  }

  def schemaEncoder[A](schema: Schema[A])(implicit config: Config = Config.default): Encoder[A] =
    Codecs.encodeSchema(schema, config)

  object CirceEncoder {

    private[circe] def charSequenceToByteChunk(chars: CharSequence): Chunk[Byte] = {
      val bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
      Chunk.fromByteBuffer(bytes)
    }

    final def encode[A](schema: Schema[A], value: A, config: Config): Chunk[Byte] =
      charSequenceToByteChunk(Codecs.encodeSchema(schema, config)(value).noSpaces)
  }

  def schemaDecoder[A](schema: Schema[A]): Decoder[A] = Codecs.decodeSchema(schema)

  object CirceDecoder {

    final def decode[A](schema: Schema[A], json: String): Either[Error, A] = {
      implicit val decoder: Decoder[A] = Codecs.decodeSchema(schema)
      parser.decode[A](json)
    }
  }

  def schemaCodec[A](schema: Schema[A])(implicit config: Config = Config.default): Codec[A] =
    Codec.from(schemaDecoder(schema), schemaEncoder(schema)(config))
}
