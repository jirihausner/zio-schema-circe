package zio.schema.codec.circe.jsoniter

import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import zio.Console._
import zio._
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.circe.VersionSpecificCirceCodecSpec._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object VersionSpecificCirceJsoniterCodecSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] =
    suite("VersionSpecificCirceJsoniterCodecSpec")(
      customSuite
    ) @@ timeout(90.seconds)

  private val customSuite = suite("custom")(
    suite("default value schema")(
      test("default value at last field") {
        implicit val decoder: Decoder[WithDefaultValue] = CirceJsoniterCodec.schemaDecoder(Schema[WithDefaultValue])
        val result = decode[WithDefaultValue]("""{"orderId": 1}""")
        assertTrue(result.isRight)
      }
    ),
    suite("enum with discrimintator")(
      test("default value at last field") {
        implicit val decoder: Decoder[Base] = CirceJsoniterCodec.schemaDecoder(Schema[Base])
        implicit val encoder: Encoder[Base] = CirceJsoniterCodec.schemaEncoder(Schema[Base])
        val value = BaseB("a", Inner(1))
        val json = """{"type":"BaseB","a":"a","b":{"i":1}}"""
        assert(decode[Base](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      }
    ),
    suite("union types")(
      test("union type of standard types") {
        val schema = Schema.chunk(DeriveSchema.gen[Int | String | Boolean])
        implicit val decoder: Decoder[Chunk[Int | String | Boolean]] = CirceJsoniterCodec.schemaDecoder(schema)
        implicit val encoder: Encoder[Chunk[Int | String | Boolean]] = CirceJsoniterCodec.schemaEncoder(schema)
        val json = """["abc",1,true]"""
        val value = Chunk[Int | String | Boolean]("abc", 1, true)
        assert(decode[Chunk[Int | String | Boolean]](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      },
      test("union type of enums") {
        val schema = Schema.chunk(Schema[Result])
        implicit val decoder: Decoder[Chunk[Result]] = CirceJsoniterCodec.schemaDecoder(schema)
        implicit val encoder: Encoder[Chunk[Result]] = CirceJsoniterCodec.schemaEncoder(schema)
        val json = """[{"res":{"Left":"Err1"}},{"res":{"Left":"Err21"}},{"res":{"Right":{"i":1}}}]"""
        val value = Chunk[Result](Result(Left(ErrorGroup1.Err1)), Result(Left(ErrorGroup2.Err21)), Result(Right(Value(1))))
        assert(decode[Chunk[Result]](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      },
      test("union type of custom types") {
        import UnionValue.given

        val schema = Schema.map(Schema[String], Schema[UnionValue])
        implicit val decoder: Decoder[Map[String, UnionValue]] = CirceJsoniterCodec.schemaDecoder(schema)
        implicit val encoder: Encoder[Map[String, UnionValue]] = CirceJsoniterCodec.schemaEncoder(schema)
        val json = """{"a":1,"b":"toto","c":true,"d":null}"""
        val value = Map("a" -> 1, "b" -> "toto", "c" -> true, "d" -> null)
        assert(decode[Map[String, UnionValue]](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      }
    )
  )
}
