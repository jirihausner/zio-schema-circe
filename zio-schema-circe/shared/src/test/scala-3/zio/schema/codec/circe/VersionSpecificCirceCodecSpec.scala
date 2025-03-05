package zio.schema.codec.circe

import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import zio.Console._
import zio._
import zio.schema._
import zio.schema.annotation._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.{assert, assertTrue, Spec, TestEnvironment, ZIOSpecDefault}

object VersionSpecificCirceCodecSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] =
    suite("VersionSpecificCirceCodecSpec")(
      customSuite
    ) @@ timeout(90.seconds)

  private val customSuite = suite("custom")(
    suite("default value schema")(
      test("default value at last field") {
        implicit val decoder: Decoder[WithDefaultValue] = CirceCodec.schemaDecoder(Schema[WithDefaultValue])
        val result = decode[WithDefaultValue]("""{"orderId": 1}""")
        assertTrue(result.isRight)
      }
    ),
    suite("enum with discrimintator")(
      test("default value at last field") {
        implicit val decoder: Decoder[Base] = CirceCodec.schemaDecoder(Schema[Base])
        implicit val encoder: Encoder[Base] = CirceCodec.schemaEncoder(Schema[Base])
        val value = BaseB("a", Inner(1))
        val json = """{"type":"BaseB","a":"a","b":{"i":1}}"""
        assert(decode[Base](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      }
    ),
    suite("union types")(
      test("union type of standard types") {
        val schema = Schema.chunk(DeriveSchema.gen[Int | String | Boolean])
        implicit val decoder: Decoder[Chunk[Int | String | Boolean]] = CirceCodec.schemaDecoder(schema)
        implicit val encoder: Encoder[Chunk[Int | String | Boolean]] = CirceCodec.schemaEncoder(schema)
        val json = """["abc",1,true]"""
        val value = Chunk[Int | String | Boolean]("abc", 1, true)
        assert(decode[Chunk[Int | String | Boolean]](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      },
      test("union type of enums") {
        val schema = Schema.chunk(Schema[Result])
        implicit val decoder: Decoder[Chunk[Result]] = CirceCodec.schemaDecoder(schema)
        implicit val encoder: Encoder[Chunk[Result]] = CirceCodec.schemaEncoder(schema)
        val json = """[{"res":{"Left":"Err1"}},{"res":{"Left":"Err21"}},{"res":{"Right":{"i":1}}}]"""
        val value = Chunk[Result](Result(Left(ErrorGroup1.Err1)), Result(Left(ErrorGroup2.Err21)), Result(Right(Value(1))))
        assert(decode[Chunk[Result]](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      },
      test("union type of custom types") {
        import UnionValue.given

        val schema = Schema.map(Schema[String], Schema[UnionValue])
        implicit val decoder: Decoder[Map[String, UnionValue]] = CirceCodec.schemaDecoder(schema)
        implicit val encoder: Encoder[Map[String, UnionValue]] = CirceCodec.schemaEncoder(schema)
        val json = """{"a":1,"b":"toto","c":true,"d":null}"""
        val value = Map("a" -> 1, "b" -> "toto", "c" -> true, "d" -> null)
        assert(decode[Map[String, UnionValue]](json))(equalTo(Right(value))) &&
        assert(encoder.apply(value).noSpaces)(equalTo(json))
      }
    )
  )

  case class WithDefaultValue(orderId: Int, description: String = "desc")

  object WithDefaultValue {
    implicit lazy val schema: Schema[WithDefaultValue] = DeriveSchema.gen[WithDefaultValue]
  }

  enum ErrorGroup1:
    case Err1
    case Err2
    case Err3

  enum ErrorGroup2:
    case Err21
    case Err22
    case Err23

  case class Value(i: Int)
  object Value:
    given Schema[Value] = DeriveSchema.gen[Value]

  case class Result(res: Either[ErrorGroup1 | ErrorGroup2, Value])
  object Result:
    given Schema[Result] = DeriveSchema.gen[Result]

  case class Inner(i: Int) derives Schema

  @discriminatorName("type")
  sealed trait Base derives Schema:
    def a: String

  case class BaseA(a: String) extends Base derives Schema

  case class BaseB(a: String, b: Inner) extends Base derives Schema

  given Schema[Null] = Schema.option[Unit].transform[Null]({ _ => null }, { _ => None })

  type UnionValue = Int | Boolean | String | Null

  object UnionValue {
    given Schema[UnionValue] = Schema.enumeration[UnionValue, CaseSet.Aux[UnionValue]](
      TypeId.Structural,
      CaseSet.caseOf[Int, UnionValue]("int")(_.asInstanceOf[Int])(_.asInstanceOf[UnionValue])(_.isInstanceOf[Int]) ++
        CaseSet.caseOf[Boolean, UnionValue]("boolean")(_.asInstanceOf[Boolean])(_.asInstanceOf[UnionValue])(_.isInstanceOf[Boolean]) ++
        CaseSet.caseOf[String, UnionValue]("string")(_.asInstanceOf[String])(_.asInstanceOf[UnionValue])(_.isInstanceOf[String]) ++
        CaseSet.caseOf[Null, UnionValue]("null")(_.asInstanceOf[Null])(_.asInstanceOf[UnionValue])(_ == null),
      Chunk(noDiscriminator())
    )
  }
}
