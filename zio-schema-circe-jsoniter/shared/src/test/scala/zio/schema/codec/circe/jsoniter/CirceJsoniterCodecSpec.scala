package zio.schema.codec.circe.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import zio.durationInt
import zio.schema._
import zio.schema.codec.circe._
import zio.schema.codec.circe.internal._
import zio.test.TestAspect._
import zio.test._

object CirceJsoniterCodecSpec extends ZIOSpecDefault with EncoderSpecs with DecoderSpecs with EncoderDecoderSpecs {

  override type Config = CirceCodec.Config

  override protected def DefaultConfig: CirceCodec.Config = CirceCodec.Config.default

  override protected def IgnoreEmptyCollectionsConfig: Config       =
    CirceCodec.Config(ignoreEmptyCollections = true)
  override protected def KeepNullsAndEmptyColleciontsConfig: Config =
    CirceCodec.Config(ignoreEmptyCollections = false, ignoreNullValues = false)
  override protected def StreamingConfig: CirceCodec.Config         =
    CirceCodec.Config(ignoreEmptyCollections = false, treatStreamsAsArrays = true)

  override protected def BinaryCodec[A]: (Schema[A], Config) => codec.BinaryCodec[A] =
    (schema: Schema[A], config: CirceCodec.Config) => CirceJsoniterCodec.schemaBasedBinaryCodec(config)(schema)

  import zio.schema.codec.circe.jsoniter.{schemaJson, schemaJsonObject, schemaJsonNumber}

  /**
   * Workaround for inconsistency between circe and jsoniter in handling Unicode
   * escaping (e.g. "\u001E" vs "\u001e").
   */
  override def stringify(str: String): String = writeToString(io.circe.Encoder.encodeString(str))(jsonC3c)

  def spec: Spec[TestEnvironment, Any] =
    suite("CirceJsoniterCodec specs")(
      encoderSuite,
      decoderSuite,
      encoderDecoderSuite,
      CirceCodecSpec.circeASTSuite,
    ) @@ timeout(180.seconds)
}
