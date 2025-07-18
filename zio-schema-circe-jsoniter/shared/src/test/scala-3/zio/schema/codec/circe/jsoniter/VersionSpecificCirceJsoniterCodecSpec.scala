package zio.schema.codec.circe.jsoniter

import io.circe.{Decoder, Encoder}
import zio.durationInt
import zio.schema.Schema
import zio.schema.codec.circe.VersionSpecificCodecSpec
import zio.schema.codec.circe.jsoniter.CirceJsoniterCodec.Configuration
import zio.test.TestAspect.timeout
import zio.test.{Spec, TestEnvironment}

object VersionSpecificCirceJsoniterCodecSpec extends VersionSpecificCodecSpec {

  implicit val config: Configuration = Configuration.default

  override protected def schemaEncoder[A: Schema]: Encoder[A] =
    CirceJsoniterCodec.schemaEncoder(config)(Schema[A])
  override protected def schemaDecoder[A: Schema]: Decoder[A] =
    CirceJsoniterCodec.schemaDecoder(config)(Schema[A])

  def spec: Spec[TestEnvironment, Any] =
    suite("VersionSpecificCirceJsoniterCodecSpec")(
      customSuite,
    ) @@ timeout(90.seconds)
}
