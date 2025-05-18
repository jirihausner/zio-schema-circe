package zio.schema.codec.circe.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import zio.durationInt
import zio.schema._
import zio.schema.codec.circe.CirceCodec.{Configuration, ExplicitConfig}
import zio.schema.codec.circe._
import zio.schema.codec.circe.internal._
import zio.test.TestAspect._
import zio.test._

object CirceJsoniterCodecSpec extends ZIOSpecDefault with EncoderSpecs with DecoderSpecs with EncoderDecoderSpecs {

  override protected def IgnoreEmptyCollectionsConfig: Configuration       =
    CirceCodec.Configuration.default.withEmptyCollectionsIgnored.withNullValuesIgnored
  override protected def KeepNullsAndEmptyColleciontsConfig: Configuration =
    CirceCodec.Configuration.default.copy(
      explicitEmptyCollections = ExplicitConfig(decoding = true),
      explicitNullValues = ExplicitConfig(decoding = true),
    )
  override protected def StreamingConfig: Configuration                    =
    CirceCodec.Configuration.default.copy(treatStreamsAsArrays = true)

  override protected def BinaryCodec[A]: (Schema[A], Configuration) => codec.BinaryCodec[A] =
    (schema: Schema[A], config: Configuration) => CirceJsoniterCodec.schemaBasedBinaryCodec(config)(schema)

  import zio.schema.codec.circe.jsoniter.{schemaJson, schemaJsonObject, schemaJsonNumber}

  /**
   * Workaround for inconsistency between circe and jsoniter in handling Unicode
   * escaping (e.g. "\u001E" vs "\u001e").
   */
  override def stringify(str: String): String = writeToString(io.circe.Encoder.encodeString(str))(jsonC3c)

  /**
   * Workaround for jsoniter being rounded up on 6th digit on JS platform.
   */
  override protected def testFloat: Spec[Any, Nothing] = test("Float") {
    import java.math.RoundingMode

    check(Gen.float) { float =>
      assertEncodesNumericToPrecision(
        Schema.Primitive(StandardType.FloatType),
        float,
        bd => {
          if (TestPlatform.isJS) bd.setScale(5, RoundingMode.HALF_UP) // less accurate on JS
          else bd.setScale(7, RoundingMode.HALF_UP)
        },
      )
    }
  }

  def spec: Spec[TestEnvironment, Any] =
    suite("CirceJsoniterCodec specs")(
      encoderSuite,
      decoderSuite,
      encoderDecoderSuite,
      CirceCodecSpec.circeASTSuite,
    ) @@ timeout(180.seconds)
}
