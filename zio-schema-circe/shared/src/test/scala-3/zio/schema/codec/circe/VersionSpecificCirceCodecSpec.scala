package zio.schema.codec.circe

import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import zio.Console.*
import zio.*
import zio.schema.*
import zio.schema.annotation.*
import zio.schema.codec.circe.CirceCodec.Configuration
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.test.{assert, assertTrue, Spec, TestEnvironment, ZIOSpecDefault}

object VersionSpecificCirceCodecSpec extends VersionSpecificCodecSpec {

  implicit val config: Configuration = Configuration.default

  override protected def schemaEncoder[A: Schema]: Encoder[A] = CirceCodec.schemaEncoder(config)(Schema[A])
  override protected def schemaDecoder[A: Schema]: Decoder[A] = CirceCodec.schemaDecoder(config)(Schema[A])

  def spec: Spec[TestEnvironment, Any] =
    suite("VersionSpecificCirceCodecSpec")(
      customSuite
    ) @@ timeout(90.seconds)
}

trait VersionSpecificCodecSpec extends ZIOSpecDefault  {

  protected def schemaEncoder[A: Schema]: Encoder[A]
  protected def schemaDecoder[A: Schema]: Decoder[A]

  protected val customSuite = suite("custom")(
    suite("default value schema")(
      test("default value at last field") {
        val result = decode("""{"orderId": 1}""")(using schemaDecoder(using Schema[WithDefaultValue]))
        assertTrue(result.isRight)
      }
    ),
    suite("enum with discriminator")(
      test("default value at last field") {
        val value = BaseB("a", Inner(1))
        val json = """{"type":"BaseB","a":"a","b":{"i":1}}"""
        assert(decode(json)(using schemaDecoder(using Schema[Base])))(equalTo(Right(value))) &&
        assert(schemaEncoder(using Schema[Base]).apply(value).noSpaces)(equalTo(json))
      }
    ),
    suite("enum with case name annotations")(
      test("case name annotations on enum values") {
        val schema1 = Schema.chunk(DeriveSchema.gen[Foo])
        val schema2 = Schema.chunk(DeriveSchema.gen[Foo2])
        val json1 = """["bar","baz","qux","Quux"]"""
        val json2 = """["Bar","baz","qux"]"""
        val value1 = Chunk[Foo](Foo.Bar, Foo.Baz, Foo.Qux, Foo.Quux)
        val value2 = Chunk[Foo2](Foo2.Bar, Foo2.Baz, Foo2.Qux)
        assert(decode(json1)(using schemaDecoder(using schema1)))(equalTo(Right(value1))) &&
        assert(schemaEncoder(using schema1).apply(value1).noSpaces)(equalTo(json1)) &&
        assert(decode(json2)(using schemaDecoder(using schema2)))(equalTo(Right(value2))) &&
        assert(schemaEncoder(using schema2).apply(value2).noSpaces)(equalTo(json2))
      }
    ),
    suite("union types")(
      test("union type of standard types") {
        val schema = Schema.chunk(DeriveSchema.gen[Int | String | Boolean])
        val json = """["abc",1,true]"""
        val value = Chunk[Int | String | Boolean]("abc", 1, true)
        assert(decode(json)(using schemaDecoder(using schema)))(equalTo(Right(value))) &&
        assert(schemaEncoder(using schema).apply(value).noSpaces)(equalTo(json))
      },
      test("union type of enums") {
        val schema = Schema.chunk(Schema[Result])
        val json = """[{"res":{"Left":"Err1"}},{"res":{"Left":"Err21"}},{"res":{"Right":{"i":1}}}]"""
        val value = Chunk[Result](Result(Left(ErrorGroup1.Err1)), Result(Left(ErrorGroup2.Err21)), Result(Right(Value(1))))
        assert(decode(json)(using schemaDecoder(using schema)))(equalTo(Right(value))) &&
        assert(schemaEncoder(using schema).apply(value).noSpaces)(equalTo(json))
      },
      test("union type of custom types") {
        import UnionValue.given

        val schema = Schema.map(Schema[String], Schema[UnionValue])
        val json = """{"a":1,"b":"toto","c":true,"d":null}"""
        val value = Map("a" -> 1, "b" -> "toto", "c" -> true, "d" -> null)
        assert(decode(json)(using schemaDecoder(using schema)))(equalTo(Right(value))) &&
        assert(schemaEncoder(using schema).apply(value).noSpaces)(equalTo(json))
      }
    ),
  )

  case class WithDefaultValue(orderId: Int, description: String = "desc")

  object WithDefaultValue {
    implicit lazy val schema: Schema[WithDefaultValue] = DeriveSchema.gen[WithDefaultValue]
  }

  enum Foo:
    @caseName("bar") @caseName("xxx") case Bar
    @caseName("baz") case Baz
    @caseName("qux") case Qux
    case Quux

  enum Foo2:
    case Bar
    @caseName("baz") @caseName("xxx") case Baz
    @caseName("qux") case Qux

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
