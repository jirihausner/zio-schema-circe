package zio.schema.codec.circe.internal

import io.circe._
import zio.prelude.NonEmptyMap
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.DecodeError
import zio.schema.codec.DecodeError.ReadError
import zio.schema.codec.circe.CirceCodec.CirceEncoder.charSequenceToByteChunk
import zio.schema.codec.circe.internal.Data._
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test._
import zio.{Cause, Chunk, Console, ZIO}

import scala.collection.immutable.ListMap

private[circe] trait DecoderSpecs extends StringUtils {

  type Config

  protected def DefaultConfig: Config
  protected def StreamingConfig: Config // should keep empty collections and treat streams as arrays

  protected def BinaryCodec[A]: (Schema[A], Config) => codec.BinaryCodec[A]

  final protected def assertDecodesToError[A](
    schema: Schema[A],
    json: CharSequence,
    error: Exception,
    config: Config = DefaultConfig,
    debug: Boolean = false,
  ): ZIO[Any, Nothing, TestResult] = {
    val stream = ZStream
      .fromChunk(charSequenceToByteChunk(json))
      .via(BinaryCodec(schema, config).streamDecoder)
      .runHead
      .exit
      .tap { exit =>
        val expected = zio.Exit.Failure(Cause.fail(ReadError(Cause.fail(error), error.getMessage)))
        (Console.printLine(s"expected: $expected") *>
          Console.printLine(s"got:      $exit")).when(debug).ignore
      }
    assertZIO(stream)(fails(equalTo(ReadError(Cause.fail(error), error.getMessage))))
  }

  final protected def assertDecodes[A](
    schema: Schema[A],
    json: CharSequence,
    value: A,
    config: Config = DefaultConfig,
    debug: Boolean = false,
  ): ZIO[Any, DecodeError, TestResult] = {
    val result = ZStream
      .fromChunk(charSequenceToByteChunk(json))
      .via(BinaryCodec(schema, config).streamDecoder)
      .runCollect
      .exit
      .tap { exit =>
        val expected = zio.Exit.Success(Chunk(value))
        (Console.printLine(s"expected: $expected") *>
          Console.printLine(s"got:      $exit")).when(debug).ignore
      }
    assertZIO(result)(succeeds(equalTo(Chunk(value))))
  }

  final protected def assertDecodesMany[A](
    schema: Schema[A],
    json: CharSequence,
    values: Chunk[A],
    config: Config = DefaultConfig,
    debug: Boolean = false,
  ): ZIO[Any, DecodeError, TestResult] = {
    val result = ZStream
      .fromChunk(charSequenceToByteChunk(json))
      .via(BinaryCodec(schema, config).streamDecoder)
      .runCollect
      .exit
      .tap { exit =>
        val expected = zio.Exit.Success(values)
        (Console.printLine(s"expected: $expected") *>
          Console.printLine(s"got:      $exit")).when(debug).ignore
      }
    assertZIO(result)(succeeds(equalTo(values)))
  }

  import PaymentMethod.WireTransfer
  import Subscription.{OneTime, Recurring}

  protected final val decoderSuite: Spec[Sized, DecodeError] = suite("decoding")(
    suite("primitive")(
      test("Unit") {
        assertDecodes(Schema[Unit], "{}", ())
      },
      suite("String")(
        test("any unicode") {
          check(Gen.string) { string => assertDecodes(Schema[String], stringify(string), string) }
        },
        test("any ascii") {
          check(Gen.asciiString) { string => assertDecodes(Schema[String], stringify(string), string) }
        },
      ),
      test("Boolean") {
        check(Gen.boolean) { boolean =>
          assertDecodes(Schema.Primitive(StandardType.BoolType), boolean.toString, boolean)
        }
      },
      test("Byte") {
        check(Gen.byte) { byte =>
          assertDecodes(Schema.Primitive(StandardType.ByteType), byte.toString, byte)
        }
      },
      test("Short") {
        check(Gen.short) { short =>
          assertDecodes(Schema.Primitive(StandardType.ShortType), short.toString, short)
        }
      },
      test("Int") {
        check(Gen.int) { int =>
          assertDecodes(Schema.Primitive(StandardType.IntType), int.toString, int)
        }
      },
      test("Long") {
        check(Gen.long) { long =>
          assertDecodes(Schema.Primitive(StandardType.LongType), long.toString, long)
        }
      } @@ TestAspect.js(ignore), // FIXME: fix accuracy on ScalaJS platform
      test("Float") {
        check(Gen.float) { float =>
          assertDecodes(Schema.Primitive(StandardType.FloatType), float.toString, float)
        }
      },
      test("Double") {
        check(Gen.double) { double =>
          assertDecodes(Schema.Primitive(StandardType.DoubleType), double.toString, double)
        }
      },
      test("Binary") {
        check(Gen.chunkOf(Gen.byte)) { bytes =>
          assertDecodes(Schema.Primitive(StandardType.BinaryType), bytes.mkString("[", ",", "]"), bytes)
        }
      },
      test("Char") {
        check(Gen.asciiChar) { char =>
          assertDecodes(Schema.Primitive(StandardType.CharType), stringify(char.toString), char)
        }
      },
      test("BigInteger") {
        check(Gen.bigIntegerJava(BigInt(0), BigInt(Int.MaxValue))) { bi =>
          assertDecodes(Schema.Primitive(StandardType.BigIntegerType), bi.toString, bi)
        }
      },
      test("BigDecimal") {
        check(Gen.bigDecimalJava(BigDecimal(0), BigDecimal(Int.MaxValue))) { bd =>
          assertDecodes(Schema.Primitive(StandardType.BigDecimalType), bd.toString, bd)
        }
      },
      test("UUID") {
        check(Gen.uuid) { uuid =>
          assertDecodes(Schema.Primitive(StandardType.UUIDType), stringify(uuid.toString), uuid)
        }
      },
      test("DayOfWeek") {
        check(Gen.dayOfWeek) { dayOfWeek =>
          assertDecodes(Schema.Primitive(StandardType.DayOfWeekType), stringify(dayOfWeek.toString), dayOfWeek)
        }
      },
      test("Duration") {
        check(Gen.finiteDuration) { duration =>
          assertDecodes(Schema.Primitive(StandardType.DurationType), stringify(duration.toString), duration)
        }
      },
      test("Instant") {
        check(Gen.instant) { instant =>
          assertDecodes(Schema.Primitive(StandardType.InstantType), stringify(instant.toString), instant)
        }
      },
      test("LocalDate") {
        check(Gen.localDate) { localDate =>
          assertDecodes(Schema.Primitive(StandardType.LocalDateType), stringify(localDate.toString), localDate)
        }
      },
      test("LocalDateTime") {
        check(Gen.localDateTime) { localDateTime =>
          assertDecodes(
            Schema.Primitive(StandardType.LocalDateTimeType),
            stringify(localDateTime.toString),
            localDateTime,
          )
        }
      },
      test("LocalTime") {
        check(Gen.localTime) { localTime =>
          assertDecodes(
            Schema.Primitive(StandardType.LocalTimeType),
            stringify(localTime.toString),
            localTime,
          )
        }
      },
      test("Month") {
        check(Gen.month) { month =>
          assertDecodes(Schema.Primitive(StandardType.MonthType), stringify(month.toString), month)
        }
      },
      test("MonthDay") {
        check(Gen.monthDay) { monthDay =>
          assertDecodes(Schema.Primitive(StandardType.MonthDayType), stringify(monthDay.toString), monthDay)
        }
      },
      test("OffsetDateTime") {
        check(Gen.offsetDateTime) { offsetDateTime =>
          assertDecodes(
            Schema.Primitive(StandardType.OffsetDateTimeType),
            stringify(offsetDateTime.toString),
            offsetDateTime,
          )
        }
      },
      test("OffsetTime") {
        check(Gen.offsetTime) { offsetTime =>
          assertDecodes(Schema.Primitive(StandardType.OffsetTimeType), stringify(offsetTime.toString), offsetTime)
        }
      },
      test("Period") {
        check(Gen.period) { period =>
          assertDecodes(Schema.Primitive(StandardType.PeriodType), stringify(period.toString), period)
        }
      },
      test("Year") {
        check(Gen.year) { year =>
          assertDecodes(Schema.Primitive(StandardType.YearType), stringify(f"${year.getValue}%+d"), year)
        }
      },
      test("YearMonth") {
        check(Gen.yearMonth) { yearMonth =>
          assertDecodes(
            Schema.Primitive(StandardType.YearMonthType),
            if (yearMonth.getYear > 9999) stringify(s"+${yearMonth.toString}")
            else stringify(yearMonth.toString),
            yearMonth,
          )
        }
      },
      test("ZoneDateTime") {
        check(Gen.zonedDateTime) { zonedDateTime =>
          assertDecodes(
            Schema.Primitive(StandardType.ZonedDateTimeType),
            stringify(zonedDateTime.toString),
            zonedDateTime,
          )
        }
      },
      test("ZoneOffset") {
        check(Gen.zoneOffset) { zoneOffset =>
          assertDecodes(Schema.Primitive(StandardType.ZoneOffsetType), stringify(zoneOffset.toString), zoneOffset)
        }
      },
      test("ZoneId") {
        check(Gen.zoneId) { zoneId =>
          assertDecodes(Schema.Primitive(StandardType.ZoneIdType), stringify(zoneId.toString), zoneId)
        }
      },
      test("Currency") {
        check(Gen.currency) { currency =>
          assertDecodes(Schema[java.util.Currency], stringify(currency.toString), currency)
        }
      } @@ TestAspect.jvmOnly,
    ),
    suite("sequence")(
      test("of primitives") {
        check(Gen.chunkOf(Gen.string)) { chunk =>
          assertDecodes(
            Schema.chunk(Schema.Primitive(StandardType.StringType)),
            chunk.map(stringify).mkString("[", ",", "]"),
            chunk,
          )
        }
      },
      test("of complex values") {
        check(Gen.chunkOf(genValue)) { chunk =>
          assertDecodes(
            Schema.chunk(Value.schema),
            chunk.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
            chunk,
          )
        }
      },
    ),
    suite("non-empty sequence")(
      test("of primitives") {
        check(genNonEmptyChunkOf(Gen.string)) { nonEmptyChunk =>
          assertDecodes(
            Schema.nonEmptyChunk(Schema.Primitive(StandardType.StringType)),
            nonEmptyChunk.map(stringify).mkString("[", ",", "]"),
            nonEmptyChunk,
          )
        }
      },
      test("of complex values") {
        check(genNonEmptyChunkOf(genValue)) { nonEmptyChunk =>
          assertDecodes(
            Schema.nonEmptyChunk(Value.schema),
            nonEmptyChunk.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
            nonEmptyChunk,
          )
        }
      },
    ),
    suite("map")(
      test("of simple keys and values") {
        assertDecodes(
          Schema.map[Int, Value],
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
          Map(0 -> Value(0, true), 1 -> Value(1, false)),
        )
      },
      test("of simple keys and values where the key's schema is lazy") {
        assertDecodes(
          Schema.map[Int, Value](Schema.defer(Schema[Int]), Schema[Value]),
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
          Map(0 -> Value(0, true), 1 -> Value(1, false)),
        )
      },
      test("of complex keys and values") {
        assertDecodes(
          Schema.map[Key, Value],
          """[[{"name":"a","index":0},{"first":0,"second":true}],[{"name":"b","index":1},{"first":1,"second":false}]]""",
          Map(Key("a", 0) -> Value(0, true), Key("b", 1) -> Value(1, false)),
        )
      },
      test("of complex keys with transformation to primitive keys") {
        assertDecodes(
          Schema.map[KeyWrapper, ValueWrapper],
          """{"wrapped_key_1":{"value":"some_value"},"wrapped_key_2":{"value":"some_other_value"}}""",
          Map(
            KeyWrapper("wrapped_key_1") -> ValueWrapper(value = "some_value"),
            KeyWrapper("wrapped_key_2") -> ValueWrapper(value = "some_other_value"),
          ),
        )
      },
    ),
    suite("non-empty map")(
      test("of simple keys and values") {
        assertDecodes(
          Schema.nonEmptyMap[Int, Value],
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
          NonEmptyMap(0 -> Value(0, true), 1 -> Value(1, false)),
        )
      },
      test("of simple keys and values where the key's schema is lazy") {
        assertDecodes(
          Schema.nonEmptyMap[Int, Value](Schema.defer(Schema[Int]), Schema[Value]),
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
          NonEmptyMap(0 -> Value(0, true), 1 -> Value(1, false)),
        )
      },
      test("of complex keys and values") {
        assertDecodes(
          Schema.nonEmptyMap[Key, Value],
          """[[{"name":"a","index":0},{"first":0,"second":true}],[{"name":"b","index":1},{"first":1,"second":false}]]""",
          NonEmptyMap(Key("a", 0) -> Value(0, true), Key("b", 1) -> Value(1, false)),
        )
      },
      test("of complex keys with transformation to primitive keys") {
        assertDecodes(
          Schema.nonEmptyMap[KeyWrapper, ValueWrapper],
          """{"wrapped_key_1":{"value":"some_value"},"wrapped_key_2":{"value":"some_other_value"}}""",
          NonEmptyMap(
            KeyWrapper("wrapped_key_1") -> ValueWrapper(value = "some_value"),
            KeyWrapper("wrapped_key_2") -> ValueWrapper(value = "some_other_value"),
          ),
        )
      },
    ) @@ ignore, // FIXME: find better test, NonEmptyMap ordering is non-deterministic
    suite("set")(
      test("of primitives") {
        check(Gen.setOf(Gen.string)) { set =>
          assertDecodes(
            Schema.set(Schema.Primitive(StandardType.StringType)),
            set.map(stringify).mkString("[", ",", "]"),
            set,
          )
        }
      },
      test("of complex values") {
        check(Gen.setOf(genValue)) { set =>
          assertDecodes(
            Schema.set(Value.schema),
            set.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
            set,
          )
        }
      },
    ) @@ ignore, // FIXME: find better test, Set ordering is non-deterministic
    suite("non-empty set")(
      test("of primitives") {
        check(genNonEmptySetOf(Gen.string)) { nonEmptySet =>
          assertDecodes(
            Schema.nonEmptySet(Schema.Primitive(StandardType.StringType)),
            nonEmptySet.map(stringify).mkString("[", ",", "]"),
            nonEmptySet,
          )
        }
      },
      test("of complex values") {
        check(genNonEmptySetOf(genValue)) { nonEmptySet =>
          assertDecodes(
            Schema.nonEmptySet(Value.schema),
            nonEmptySet.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
            nonEmptySet,
            debug = true,
          )
        }
      },
    ) @@ ignore, // FIXME: find better test, NonEmptySet ordering is non-deterministic
    suite("transform")(
      test("of simple string to its size as int") {
        check(Gen.string) { string =>
          assertDecodes(
            Schema.Transform[String, Int, Unit](
              Schema[String],
              str => Right(str.size),
              _ => Left("undefined"),
              Chunk.empty[Any],
              (),
            ),
            stringify(string),
            string.size,
          )
        }
      },
      test("of failing as undefined") {
        check(Gen.int) { int =>
          assertDecodesToError(
            Schema.Transform[Int, String, Unit](
              Schema[Int],
              _ => Left("undefined"),
              str => Right(str.size),
              Chunk.empty[Any],
              (),
            ),
            int.toString,
            DecodingFailure("undefined", Nil),
          )
        }
      },
    ),
    suite("tuple")(
      test("of primitives") {
        check(Gen.string <*> Gen.int) { case (string, int) =>
          assertDecodes(
            Schema.Tuple2(
              Schema.Primitive(StandardType.StringType),
              Schema.Primitive(StandardType.IntType),
            ),
            s"""[${stringify(string)},$int]""",
            (string, int),
          )
        }
      },
      test("of complex values") {
        check(Gen.string <*> genValue) { case (string, value) =>
          assertDecodes(
            Schema.Tuple2(
              Schema.Primitive(StandardType.StringType),
              Value.schema,
            ),
            s"""[${stringify(string)},${s"""{"first":${value.first},"second":${value.second}}"""}]""",
            (string, value),
          )
        }
      },
    ),
    suite("optional")(
      test("of some primitive") {
        check(Gen.string) { string =>
          assertDecodes(Schema.Optional(Schema.Primitive(StandardType.StringType)), stringify(string), Some(string))
        }
      },
      test("of absent primitive") {
        assertDecodes(Schema.Optional(Schema.Primitive(StandardType.StringType)), "null", None)
      },
      test("of some complex value") {
        check(genValue) { value =>
          assertDecodes(
            Schema.Optional(Value.schema),
            s"""{"first":${value.first},"second":${value.second}}""",
            Some(value),
          )
        }
      },
      test("of absent complex value") {
        assertDecodes(Schema.Optional(Value.schema), "null", None)
      },
    ),
    suite("fail")(
      test("of any cause") {
        assertDecodesToError(Schema.Fail[Exception]("Failed"), "null", DecodingFailure("Failed", Nil))
      },
    ),
    suite("generic record")(
      test("with extra fields") {
        assertDecodes(
          recordSchema,
          """{"foo":"s","bar":1,"baz":2}""",
          ListMap[String, Any]("foo" -> "s", "bar" -> 1),
        )
      },
      test("with missing fields") {
        assertDecodes(
          RecordExample.schema,
          """{"$f1":"test"}""",
          RecordExample(f1 = Some("test"), f2 = None),
        )
      },
      test("aliased field") {
        assertDecodes(
          RecordExample.schema,
          """{"$f1":"test", "field2":"alias"}""",
          RecordExample(f1 = Some("test"), f2 = Some("alias")),
        )
      },
      test("reject extra fields") {
        assertDecodesToError(
          RecordExample.schema.annotate(rejectExtraFields()),
          """{"$f1":"test", "extraField":"extra"}""",
          DecodingFailure("Unexpected field: extraField", Nil),
        )
      },
      test("optional field with schema or annotated default value") {
        assertDecodes(
          RecordExampleWithOptField.schema,
          """{"$f1":"test"}""",
          RecordExampleWithOptField(f1 = Some("test"), f2 = None, f4 = "", f5 = "hello"),
        )
      },
    ),
    suite("either")(
      test("left") {
        check(Gen.int) { int =>
          assertDecodes(
            Schema.Either(Schema[Int], Schema[String]),
            s"""{"Left":$int}""",
            Left(int),
          )
        }
      },
      test("right") {
        check(Gen.string) { string =>
          assertDecodes(
            Schema.Either(Schema[Int], Schema[String]),
            s"""{"Right":${stringify(string)}}""",
            Right(string),
          )
        }
      },
    ),
    suite("fallback")(
      test("correctly fallbacks to left") {
        check(Gen.int) { int =>
          assertDecodes(
            Schema.Fallback(Schema[Int], Schema[String]),
            int.toString,
            Fallback.Left(int),
          )
        }
      },
      test("correctly fallbacks to right") {
        check(Gen.string) { string =>
          assertDecodes(
            Schema.Fallback(Schema[Int], Schema[String]),
            stringify(string),
            Fallback.Right(string),
          )
        }
      },
      test("correctly fallbacks to left with full decode") {
        check(Gen.int) { int =>
          assertDecodes(
            Schema.Fallback(Schema[Int], Schema[String], fullDecode = true),
            s"""[$int,$int]""",
            Fallback.Left(int),
          )
        }
      },
      test("correctly fallbacks to right with full decode") {
        check(Gen.string) { string =>
          assertDecodes(
            Schema.Fallback(Schema[Int], Schema[String], fullDecode = true),
            s"""[${stringify(string)},${stringify(string)}]""",
            Fallback.Right(string),
          )
        }
      },
      test("correctly fallbacks to both with full decode") {
        check(Gen.int <*> Gen.string) { case (int, string) =>
          assertDecodes(
            Schema.Fallback(Schema[Int], Schema[String], fullDecode = true),
            s"""[$int,${stringify(string)}]""",
            Fallback.Both(int, string),
          )
        }
      },
    ),
    suite("lazy")(
      test("of primitive") {
        check(Gen.string) { string =>
          assertDecodes(
            Schema.defer(Schema[String]),
            stringify(string),
            string,
          )
        }
      },
      test("of complex values") {
        check(genValue) { value =>
          assertDecodes(
            Schema.defer(Value.schema),
            s"""{"first":${value.first},"second":${value.second}}""",
            value,
          )
        }
      },
    ),
    suite("record")(
      test("of primitives") {
        assertDecodes(
          recordSchema,
          """{"foo":"s","bar":1}""",
          ListMap[String, Any]("foo" -> "s", "bar" -> 1),
        )
      },
      test("of records") {
        assertDecodes(
          nestedRecordSchema,
          """{"l1":"s","l2":{"foo":"s","bar":1}}""",
          ListMap[String, Any]("l1" -> "s", "l2" -> ListMap("foo" -> "s", "bar" -> 1)),
        )
      },
      test("case class") {
        check(searchRequestGen) { request =>
          assertDecodes(
            searchRequestSchema,
            s"""{"query":"${request.query}","size":${request.size},"page":${request.page}""" +
              request.nextPage
                .map(x => s""","nextPage":${stringify(x)}}""")
                .getOrElse("""}"""),
            request,
          )
        }
      },
      test("case object") {
        assertDecodes(schemaObject, "{}", Singleton)
      },
      test("record with option fields encoded as null") {
        assertDecodes(
          recordWithOptionSchema,
          """{"foo":"s","bar":null}""",
          ListMap[String, Any]("foo" -> Some("s"), "bar" -> None),
        )
      },
      test("case class with option fields omitted when empty") {
        assertDecodes(
          WithOptionFields.schema,
          """{"a":"s"}""",
          WithOptionFields(Some("s"), None),
        )
      },
      test("reject extra fields") {
        assertDecodesToError(
          PersonWithRejectExtraFields.schema,
          """{"name":"test","age":10,"extraField":10}""",
          DecodingFailure("Extra field: extraField", Nil),
        ) &>
          assertDecodesToError(
            schemaObject.annotate(rejectExtraFields()),
            """{"extraField":10}""",
            DecodingFailure("Extra field: extraField", Nil),
          )
      },
      test("transient field annotation") {
        assertDecodes(
          searchRequestWithTransientFieldSchema,
          """{"query":"foo","page":10,"size":20,"nextPage":"bar"}""",
          SearchRequestWithTransientField("foo", 10, 20, "bar"),
        )
      },
      test("transient field annotation with default value in class definition") {
        assertDecodes(
          searchRequestWithTransientFieldSchema,
          """{"query":"test","page":0,"size":10}""",
          SearchRequestWithTransientField("test", 0, 10),
        )
      },
      test("transient field annotation with default value implicitly available for the field type") {
        case class CaseClassWithTransientField(transient: String)
        assertDecodes(
          Schema.CaseClass1[String, CaseClassWithTransientField](
            id0 = TypeId.fromTypeName("SearchRequestWithTransientField"),
            field0 = Schema.Field(
              name0 = "transient",
              schema0 = Schema[String],
              get0 = _.transient,
              set0 = (x, transient) => x.copy(transient = transient),
              annotations0 = Chunk(new transientField()),
            ),
            defaultConstruct0 = new CaseClassWithTransientField(_),
          ),
          """{}""",
          CaseClassWithTransientField(Schema[String].defaultValue.toOption.get),
        )
      },
      test("fieldDefaultValue") {
        assertDecodes(
          fieldDefaultValueSearchRequestSchema,
          """{"query":"test","page":0,"size":10}""",
          FieldDefaultValueSearchRequest("test", 0, 10, "test"),
        )
      },
      test("backticked field name") {
        assertDecodes(
          BacktickedFieldName.schema,
          """{"x-api-key":"test"}""",
          BacktickedFieldName("test"),
        )
      },
      test("field name with alias - id") {
        assertDecodes(
          Order.schema,
          """{"id":1,"value":10,"description":"test"}""",
          Order(1, BigDecimal.valueOf(10), "test"),
        )
      },
      test("field name with alias - order_id") {
        assertDecodes(
          Order.schema,
          """{"id":1,"value":10,"description":"test"}""",
          Order(1, BigDecimal.valueOf(10), "test"),
        )
      },
      test("field name with alias - no alias") {
        assertDecodes(
          Order.schema,
          """{"orderId":1,"value":10,"description":"test"}""",
          Order(1, BigDecimal.valueOf(10), "test"),
        )
      },
      test("with option fields encoded as null") {
        assertDecodes(
          recordWithOptionSchema,
          """{"foo":"s","bar":null}""",
          ListMap[String, Any]("foo" -> Some("s"), "bar" -> None),
        )
      },
      test("with transient fields encoded as implicitly available schema default values") {
        assertDecodes(
          recordWithTransientSchema,
          """{}""",
          ListMap[String, Any]("foo" -> "", "bar" -> 0),
        )
      },
      test("case class with option fields encoded as null") {
        assertDecodes(
          WithOptionFields.schema,
          """{"a":"s","b":null}""",
          WithOptionFields(Some("s"), None),
        )
      },
      test("case class with int option field present (at end) from pretty printed json") {
        assertDecodes(
          WithOptionFields.schema,
          """
            |{
            |  "a": "s",
            |  "b": 1
            |}
            |""".stripMargin,
          WithOptionFields(Some("s"), Some(1)),
        )
      },
      test("case class with option fields omitted when empty") {
        assertDecodes(
          WithOptionFields.schema,
          """{"a":"s"}""",
          WithOptionFields(Some("s"), None),
        )
      },
      test("case class with option fields accepts empty json object as value") {
        assertDecodesToError(
          WithOptionFields.schema,
          """{"a":"s", "b":{}}""",
          DecodingFailure("Failed to decode field: b", Nil),
        )
      },
      test("case class with complex option field rejects empty json object as value") {
        assertDecodesToError(
          WithComplexOptionField.schema,
          """{"order":{}}""",
          DecodingFailure("Failed to decode field: order", Nil),
        )
      },
      test("case class with complex option field correctly decodes") {
        assertDecodes(
          WithComplexOptionField.schema,
          """{"order":{"id":1,"value":10,"description":"test"}}""",
          WithComplexOptionField(Some(Order(1, BigDecimal.valueOf(10), "test"))),
        )
      },
      suite("case class with more than 64 fields")(
        test("decodes required and optional fields") {
          assertDecodes(
            BigProduct.schema,
            """{"f00":true}""",
            BigProduct(f00 = true, f67 = None, f68 = Nil, f69 = Vector.empty),
          )
        },
        test("fails when missing required fields") {
          assertDecodesToError(
            BigProduct.schema,
            """{}""",
            DecodingFailure("Missing field: f00", Nil),
          )
        },
        test("rejects extra fields") {
          assertDecodesToError(
            BigProduct.schema.annotate(rejectExtraFields()),
            """{"f00":true,"extraField":10}""",
            DecodingFailure("Unexpected field: extraField", Nil),
          )
        },
        test("rejects duplicated fields") {
          assertDecodesToError(
            BigProduct.schema,
            """{"f00":true,"f01":10,"f-01":8}""",
            DecodingFailure("Duplicate field: f01", Nil),
          )
        },
        test("decodes field name with alias - id") {
          assertDecodes(
            BigProduct.schema,
            """{"f00":true,"f-01":123}""",
            BigProduct(f00 = true, f01 = Some(123.toByte), f67 = None, f68 = Nil, f69 = Vector.empty),
          )
        },
      ),
      test("recursive data structure")(
        assertDecodes(
          Schema[Recursive],
          """{"n":{"n":null}}""",
          Recursive(Some(Recursive(None))),
        ),
      ),
    ),
    suite("enumeration")(
      test("of primitives") {
        assertDecodes(
          enumSchema,
          """{"string":"foo"}""",
          "foo",
        )
      },
      test("ADT") {
        assertDecodes(
          Schema[Enumeration],
          """{"oneOf":{"StringValue":{"value":"foo"}}}""",
          Enumeration(StringValue("foo")),
        )
      },
      test("ADT with annotation") {
        assertDecodes(
          Schema[Enumeration2],
          """{"oneOf":{"_type":"StringValue2","value":"foo2"}}""",
          Enumeration2(StringValue2("foo2")),
        )
      },
      suite("with discriminator")(
        test("case name annotation") {
          assertDecodes(
            Subscription.schema,
            """{"type":"recurring","period":"monthly","amount":100}""",
            Recurring("monthly", 100),
          )
        },
        test("case name annotation with empty fields") {
          assertDecodes(
            Subscription.schema,
            """{"type":"unlimited"}""",
            Subscription.Unlimited(None),
          )
        },
        test("case name aliases - first alias") {
          assertDecodes(
            Subscription.schema,
            """{"type":"one_time","amount":1000}""",
            OneTime(1000),
          )
        },
        test("case name aliases - second alias") {
          assertDecodes(
            Subscription.schema,
            """{"type":"onetime","amount":1000}""",
            OneTime(1000),
          )
        },
        test("case name aliases - type in the middle") {
          assertDecodes(
            Subscription.schema,
            """{"period":"monthly","type":"recurring","amount":100}""",
            Recurring("monthly", 100),
          )
        },
        test("case name aliases - type in the last place") {
          assertDecodes(
            Subscription.schema,
            """{"amount":1000,"type":"onetime"}""",
            OneTime(1000),
          )
        },
        test("case name - illegal discriminator value") {
          assertDecodesToError(
            Subscription.schema,
            """{"amount":1000,"type":123}""",
            DecodingFailure("Malformed subtype: 123", List(CursorOp.DownField("type"))),
          )
        },
        test("case name - missing discriminator field") {
          assertDecodesToError(
            Subscription.schema,
            """{"amount":1000}""",
            DecodingFailure("Missing subtype: type", Nil),
          )
        },
        test("case name - empty fields") {
          assertDecodes(
            Subscription.schema,
            """{"type":"unlimited"}""",
            Subscription.Unlimited(None),
          )
        },
        suite("of case classes and case objects with more than 64 cases")(
          test("with caseName") {
            assertDecodes(
              Schema[BigEnum3],
              """{"b":123,"type":"Case_00"}""",
              BigEnum3.Case00(123.toByte),
            ) &>
              assertDecodesToError(
                Schema[BigEnum3],
                """{"type":"Case00"}""",
                DecodingFailure("Unrecognized subtype: Case00", List(CursorOp.DownField("type"))),
              )
          },
          test("with caseAliases") {
            assertDecodes(
              Schema[BigEnum3],
              """{"type":"Case-00","b":123}""",
              BigEnum3.Case00(123.toByte),
            )
          },
          test("fails on missing discriminator field") {
            assertDecodesToError(Schema[BigEnum3], """{"b":123}""", DecodingFailure("Missing subtype: type", Nil)) &>
              assertDecodesToError(Schema[BigEnum3], """{}""", DecodingFailure("Missing subtype: type", Nil))
          },
          test("fails on invalid case") {
            assertDecodesToError(
              Schema[BigEnum3],
              """{"type":"CaseXX"}""",
              DecodingFailure("Unrecognized subtype: CaseXX", List(CursorOp.DownField("type"))),
            )
          },
        ),
      ),
      suite("without discriminator")(
        test("case name annotation") {
          assertDecodes(
            PaymentMethod.schema,
            """{"wire_transfer":{"accountNumber":"foo","bankCode":"bar"}}""",
            WireTransfer("foo", "bar"),
          )
        },
        test("missing discriminator") {
          assertDecodesToError(
            PaymentMethod.schema,
            "{}",
            DecodingFailure("Missing subtype", Nil),
          )
        },
        test("illegal discriminator case") {
          assertDecodesToError(
            PaymentMethod.schema,
            """{"cash":{}}""",
            DecodingFailure("Unrecognized subtype: cash", Nil),
          )
        },
        suite("of case classes and case objects with more than 64 cases")(
          test("with caseName") {
            assertDecodes(
              Schema[BigEnum2],
              """{"Case_00":{"b":123}}""",
              BigEnum2.Case00(123.toByte),
            ) &>
              assertDecodesToError(
                Schema[BigEnum2],
                """{"Case00":{}}""",
                DecodingFailure("Unrecognized subtype: Case00", Nil),
              )
          },
          test("with caseAliases") {
            assertDecodes(
              Schema[BigEnum2],
              """{"Case-00":{"b":123}}""",
              BigEnum2.Case00(123.toByte),
            )
          },
          test("no discriminator key") {
            assertDecodesToError(Schema[BigEnum2], "{}", DecodingFailure("Missing subtype", Nil))
          },
          test("invalid case") {
            assertDecodesToError(
              Schema[BigEnum2],
              """{"CaseXX":{}}""",
              DecodingFailure("Unrecognized subtype: CaseXX", Nil),
            )
          },
        ),
      ),
      suite("with no discriminator")(
        test("decodes first case") {
          assertDecodes(
            Prompt.schema,
            """{"value":"hello"}""",
            Prompt.Single("hello"),
          )
        },
        test("decodes second case") {
          assertDecodes(
            Prompt.schema,
            """{"value":["hello","world"]}""",
            Prompt.Multiple(List("hello", "world")),
          )
        },
        test("fails on unrecognized case") {
          assertDecodesToError(
            Prompt.schema,
            """{"value":5}""",
            DecodingFailure("None of the subtypes could decode the data", Nil),
          )
        },
      ),
      test("respects the case name annotation") {
        assertDecodes(
          Enum23Cases.schema,
          """{"NumberOne":{"value":"foo"}}""",
          Enum23Cases.Case1("foo"),
        )
      },
      test("respects case aliases") {
        assertDecodes(
          Enum23Cases.schema,
          """{"One":{"value":"foo"}}""",
          Enum23Cases.Case1("foo"),
        )
      },
    ),
    suite("dynamic direct mapping")(
      test("record") {
        assertDecodes(
          Schema.dynamicValue.annotate(directDynamicMapping()),
          """{"foo":"s","bar":1}""",
          DynamicValue.Record(
            TypeId.Structural,
            ListMap(
              "foo" -> DynamicValue.Primitive("s", StandardType.StringType),
              "bar" -> DynamicValue.Primitive(1L, StandardType.LongType),
            ),
          ),
        )
      },
    ),
    suite("empty collections fields")(
      test("map") {
        assertDecodes(
          Schema[ListAndMapAndOption],
          """{"list":[]}""",
          ListAndMapAndOption(Nil, Map.empty, None),
        )
      },
      test("list") {
        assertDecodes(
          Schema[ListAndMapAndOption],
          """{"map":{}}""",
          ListAndMapAndOption(Nil, Map.empty, None),
        )
      },
      test("set") {
        assertDecodes(Schema[SetWrapper], """{}""", SetWrapper(Set.empty))
      },
      test("vector") {
        assertDecodes(Schema[VectorWrapper], """{}""", VectorWrapper(Vector.empty))
      },
      test("chunck") {
        assertDecodes(Schema[ChunkWrapper], """{}""", ChunkWrapper(Chunk.empty))
      },
    ),
    suite("streams")(
      suite("of integers")(
        test("decodes a stream with multiple integers separated by newlines") {
          assertDecodesMany(Schema[Int], "1\n2\n3\n4\n5", Chunk.fromIterable(1 to 5))
        },
        test("decodes a stream with multiple integers separated by spaces") {
          assertDecodesMany(Schema[Int], "1 2 3 4 5", Chunk.fromIterable(1 to 5))
        },
        test("decodes a stream with multiple integers separated by commas and other non JSON number characters") {
          assertDecodesMany(Schema[Int], "1 2, 3;;; 4x5", Chunk.fromIterable(1 to 5), debug = true)
        } @@ ignore, // FIXME: fails but should work
        test("decodes a stream with multiple integers encoded as an array") {
          assertDecodesMany(Schema[Int], "[1,2,3,4,5]", Chunk.fromIterable(1 to 5), StreamingConfig, debug = true)
        } @@ ignore, // FIXME: fails but should work
        test("decodes a stream with multiple integers encoded as an array with additional whitespace") {
          assertDecodesMany(
            Schema[Int],
            """
              |   [1,
              |2,3,
              |4,   5]   """.stripMargin,
            Chunk.fromIterable(1 to 5),
            StreamingConfig,
            debug = true,
          )
        } @@ ignore, // FIXME: fails but should work
      ),
      suite("of booleans")(
        test("decodes a stream with multiple booleans separated by newlines") {
          assertDecodesMany(Schema[Boolean], "true\ntrue\nfalse", Chunk(true, true, false))
        },
        test("decodes a stream with multiple booleans separated by spaces") {
          assertDecodesMany(Schema[Boolean], "true true false", Chunk(true, true, false))
        },
        test("decodes a stream with multiple booleans as an array") {
          assertDecodesMany(
            Schema[Boolean],
            "[true, true, false]",
            Chunk(true, true, false),
            StreamingConfig,
          )
        },
        test(
          "decodes a stream with multiple booleans separated by commas and other non JSON boolean characters and not separated at all",
        ) {
          assertDecodesMany(
            Schema[Boolean],
            "true true, falsefalse",
            Chunk(true, true, false, false),
          )
        },
      ),
      suite("of strings")(
        test("decodes a stream with multiple strings separated by newlines") {
          assertDecodesMany(Schema[String], "\"a\"\n\"b\"\n\"c\"", Chunk("a", "b", "c"))
        },
        test("decodes a stream with multiple strings as an array") {
          assertDecodesMany(
            Schema[String],
            "[\"a\", \"b\",\n\"c\"]",
            Chunk("a", "b", "c"),
            StreamingConfig,
          )
        },
        test("decodes a stream with multiple strings separated by spaces, commas and not separated at all") {
          assertDecodesMany(Schema[String], """"a" "b","c""d"""", Chunk("a", "b", "c", "d"))
        },
      ),
      suite("stream of records")(
        test("decodes a stream with multiple records separated by newlines") {
          assertDecodesMany(
            Person.schema,
            """{"name":"Alice","age":1}
              |{"name":"Bob","age":2}
              |{"name":"Charlie","age":3}""".stripMargin,
            Chunk(
              Person("Alice", 1),
              Person("Bob", 2),
              Person("Charlie", 3),
            ),
          )
        },
        test("decodes a stream with multiple records, not separated with internal newlines") {
          assertDecodesMany(
            Person.schema,
            """{"name":"Alice","age":1}{"name":"Bob",
              |"age"
              |:2}{"name":"Charlie","age":3}""".stripMargin,
            Chunk(
              Person("Alice", 1),
              Person("Bob", 2),
              Person("Charlie", 3),
            ),
          )
        },
        test("decodes a stream with multiple records formatted as an array") {
          assertDecodesMany(
            Person.schema,
            """[{"name":"Alice","age":1},   {"name":"Bob","age":2},
              |{"name":"Charlie","age"
              |: 3}]""".stripMargin,
            Chunk(
              Person("Alice", 1),
              Person("Bob", 2),
              Person("Charlie", 3),
            ),
            StreamingConfig,
          )
        },
        test("decodes a stream with no records") {
          assertDecodesMany(Person.schema, "", Chunk.empty)
        },
        test("decodes a stream with no records from an array") {
          assertDecodesMany(Person.schema, "   [ ]  ", Chunk.empty, StreamingConfig)
        },
      ),
    ),
  )
}
