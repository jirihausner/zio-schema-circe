package zio.schema.codec.circe.jsoniter

import io.circe.Decoder
import io.circe.parser.decode
import zio.durationInt
import zio.schema.Schema
import zio.schema.codec.circe.CirceCodecJVMSpec.{RecordExample, RecordExample2}
import zio.test.Assertion.{equalTo, isRight}
import zio.test.TestAspect.timeout
import zio.test._

object CirceJsoniterCodecJVMSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] =
    suite("CirceJsoniterCodec JVM Spec")(
      decoderSuite,
    ) @@ TestAspect.jvmOnly @@ timeout(180.seconds)

  private val decoderSuite = suite("decoding")(
    suite("decodes record with more than 22 fields")(
      test("successfully if missing fields in the json payload are populated with their default values") {
        val exampleSchema = zio.schema.DeriveSchema.gen[RecordExample]
        val string        = """{"f1": "test"}"""
        assertDecodesJson(exampleSchema, RecordExample(Some("test")), string)
      },
      test("with failure if a field with no default value is missing in the json payload") {
        val exampleSchema = zio.schema.DeriveSchema.gen[RecordExample2]
        val string        = """{"f1": "test"}"""
        assertDecodesJsonFailure(exampleSchema, string)
      },
    ),
  )

  private def assertDecodesJson[A](schema: Schema[A], value: A, json: String) = {
    implicit val decoder: Decoder[A] = CirceJsoniterCodec.schemaDecoder(schema)
    val either                       = decode[A](json)
    zio.test.assert(either)(isRight(equalTo(value)))
  }

  private def assertDecodesJsonFailure[A](schema: Schema[A], json: String) = {
    implicit val decoder: Decoder[A] = CirceJsoniterCodec.schemaDecoder(schema)
    val either                       = decode[A](json)
    zio.test.assertTrue(either.isLeft)
  }
}
