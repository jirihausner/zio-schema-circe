package zio.schema.codec.circe.jsoniter

import zio.durationInt
import zio.schema._
import zio.schema.codec.circe.CirceCodec.ExplicitConfig
import zio.schema.codec.circe._
import zio.schema.codec.circe.internal._
import zio.test.TestAspect._
import zio.test._

object CirceJsoniterCodecSpec extends ZIOSpecDefault with EncoderSpecs with DecoderSpecs with EncoderDecoderSpecs {

  override type Config = CirceCodec.Configuration

  override protected def DefaultConfig: CirceCodec.Configuration = CirceCodec.Configuration.default

  override protected def IgnoreEmptyCollectionsConfig: Config       =
    CirceCodec.Configuration.default.ignoreEmptyCollections.ignoreNullValues
  override protected def KeepNullsAndEmptyColleciontsConfig: Config =
    CirceCodec.Configuration.default.copy(
      explicitEmptyCollections = ExplicitConfig(decoding = true),
      explicitNullValues = ExplicitConfig(decoding = true),
    )
  override protected def StreamingConfig: Config                    =
    CirceCodec.Configuration.default.copy(treatStreamsAsArrays = true)

  override protected def BinaryCodec[A]: (Schema[A], Config) => codec.BinaryCodec[A] =
    (schema: Schema[A], config: CirceCodec.Configuration) => CirceJsoniterCodec.schemaBasedBinaryCodec(config)(schema)

  import zio.schema.codec.circe.jsoniter.{schemaJson, schemaJsonObject, schemaJsonNumber}

  def spec: Spec[TestEnvironment, Any] =
    suite("CirceJsoniterCodec specs")(
      encoderSuite,
      decoderSuite,
      encoderDecoderSuite,
      CirceCodecSpec.circeASTSuite,
    ) @@ timeout(180.seconds)
}
