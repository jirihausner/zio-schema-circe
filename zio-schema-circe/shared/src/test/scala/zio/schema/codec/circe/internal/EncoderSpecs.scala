package zio.schema.codec.circe.internal

import zio.prelude.NonEmptyMap
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.circe.CirceCodec.CirceEncoder.charSequenceToByteChunk
import zio.schema.codec.circe.internal.Data._
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test._
import zio.{Chunk, Console, ZIO}

import scala.collection.immutable.ListMap

private[circe] trait EncoderSpecs {

  type Config

  protected def DefaultConfig: Config // should keep empty collections but ignore nulls

  protected def IgnoreEmptyCollectionsConfig: Config       // should ignore empty collections
  protected def KeepNullsAndEmptyColleciontsConfig: Config // should keep nulls and empty collections
  protected def StreamingConfig: Config                    // should keep empty collections and treat streams as arrays

  protected def BinaryCodec[A]: (Schema[A], Config) => codec.BinaryCodec[A]

  final protected def assertEncodes[A](
    schema: Schema[A],
    value: A,
    json: CharSequence,
    config: Config = DefaultConfig,
    debug: Boolean = false,
  ): ZIO[Any, Nothing, TestResult] = {
    val stream = ZStream
      .succeed(value)
      .via(BinaryCodec(schema, config).streamEncoder)
      .runCollect
      .tap { chunk =>
        (Console.printLine(s"expected: $json") *>
          Console.printLine(s"got:      ${new String(chunk.toArray)}")).when(debug).ignore
      }
    assertZIO(stream)(equalTo(charSequenceToByteChunk(json)))
  }

  final protected def assertEncodesMany[A](
    schema: Schema[A],
    values: Seq[A],
    json: CharSequence,
    config: Config = DefaultConfig,
    debug: Boolean = false,
  ): ZIO[Any, Nothing, TestResult] = {
    val stream = ZStream
      .fromIterable(values)
      .via(BinaryCodec(schema, config).streamEncoder)
      .runCollect
      .tap { chunk =>
        (Console.printLine(s"expected: $json") *>
          Console.printLine(s"got:      ${new String(chunk.toArray)}")).when(debug).ignore
      }
    assertZIO(stream)(equalTo(charSequenceToByteChunk(json)))
  }

  import PaymentMethod.{PayPal, WireTransfer}
  import Subscription.Recurring

  protected final val encoderSuite: Spec[Sized, Nothing] = suite("encoding")(
    suite("primitive")(
      test("Unit") {
        assertEncodes(Schema[Unit], (), "{}")
      },
      suite("String")(
        test("any unicode")(
          check(Gen.string) { string => assertEncodes(Schema[String], string, stringify(string)) },
        ),
        test("any ascii")(
          check(Gen.asciiString) { string => assertEncodes(Schema[String], string, stringify(string)) },
        ),
      ),
      test("Boolean") {
        check(Gen.boolean) { boolean =>
          assertEncodes(Schema.Primitive(StandardType.BoolType), boolean, boolean.toString)
        }
      },
      test("Byte") {
        check(Gen.byte) { byte =>
          assertEncodes(Schema.Primitive(StandardType.ByteType), byte, byte.toString)
        }
      },
      test("Short") {
        check(Gen.short) { short =>
          assertEncodes(Schema.Primitive(StandardType.ShortType), short, short.toString)
        }
      },
      test("Int") {
        check(Gen.int) { int =>
          assertEncodes(Schema.Primitive(StandardType.IntType), int, int.toString)
        }
      },
      test("Long") {
        check(Gen.long) { long =>
          assertEncodes(Schema.Primitive(StandardType.LongType), long, long.toString)
        }
      },
      test("Float") {
        check(Gen.float) { float =>
          assertEncodes(Schema.Primitive(StandardType.FloatType), float, float.toString)
        }
      },
      test("Double") {
        check(Gen.double) { double =>
          assertEncodes(Schema.Primitive(StandardType.DoubleType), double, double.toString)
        }
      },
      test("Binary") {
        check(Gen.chunkOf(Gen.byte)) { bytes =>
          assertEncodes(Schema.Primitive(StandardType.BinaryType), bytes, bytes.mkString("[", ",", "]"))
        }
      },
      test("Char") {
        check(Gen.asciiChar) { char =>
          assertEncodes(Schema.Primitive(StandardType.CharType), char, stringify(char.toString))
        }
      },
      test("BigInteger") {
        check(Gen.bigIntegerJava(BigInt(0), BigInt(Int.MaxValue))) { bi =>
          assertEncodes(Schema.Primitive(StandardType.BigIntegerType), bi, bi.toString)
        }
      },
      test("BigDecimal") {
        check(Gen.bigDecimalJava(BigDecimal(0), BigDecimal(Int.MaxValue))) { bd =>
          assertEncodes(Schema.Primitive(StandardType.BigDecimalType), bd, bd.toString)
        }
      },
      test("UUID") {
        check(Gen.uuid) { uuid =>
          assertEncodes(Schema.Primitive(StandardType.UUIDType), uuid, stringify(uuid.toString))
        }
      },
      test("DayOfWeek") {
        check(Gen.dayOfWeek) { dayOfWeek =>
          assertEncodes(Schema.Primitive(StandardType.DayOfWeekType), dayOfWeek, stringify(dayOfWeek.toString))
        }
      },
      test("Duration") {
        check(Gen.finiteDuration) { duration =>
          assertEncodes(Schema.Primitive(StandardType.DurationType), duration, stringify(duration.toString))
        }
      },
      test("Instant") {
        check(Gen.instant) { instant =>
          assertEncodes(Schema.Primitive(StandardType.InstantType), instant, stringify(instant.toString))
        }
      },
      test("LocalDate") {
        check(Gen.localDate) { localDate =>
          assertEncodes(Schema.Primitive(StandardType.LocalDateType), localDate, stringify(localDate.toString))
        }
      },
      test("LocalDateTime") {
        check(Gen.localDateTime) { localDateTime =>
          assertEncodes(
            Schema.Primitive(StandardType.LocalDateTimeType),
            localDateTime,
            stringify(localDateTime.toString),
          )
        }
      },
      test("LocalTime") {
        check(Gen.localTime) { localTime =>
          assertEncodes(
            Schema.Primitive(StandardType.LocalTimeType),
            localTime,
            stringify(localTime.toString),
          )
        }
      },
      test("Month") {
        check(Gen.month) { month =>
          assertEncodes(Schema.Primitive(StandardType.MonthType), month, stringify(month.toString))
        }
      },
      test("MonthDay") {
        check(Gen.monthDay) { monthDay =>
          assertEncodes(Schema.Primitive(StandardType.MonthDayType), monthDay, stringify(monthDay.toString))
        }
      },
      test("OffsetDateTime") {
        check(Gen.offsetDateTime) { offsetDateTime =>
          assertEncodes(
            Schema.Primitive(StandardType.OffsetDateTimeType),
            offsetDateTime,
            stringify(offsetDateTime.toString),
          )
        }
      },
      test("OffsetTime") {
        check(Gen.offsetTime) { offsetTime =>
          assertEncodes(Schema.Primitive(StandardType.OffsetTimeType), offsetTime, stringify(offsetTime.toString))
        }
      },
      test("Period") {
        check(Gen.period) { period =>
          assertEncodes(Schema.Primitive(StandardType.PeriodType), period, stringify(period.toString))
        }
      },
      test("Year") {
        check(Gen.year) { year =>
          assertEncodes(Schema.Primitive(StandardType.YearType), year, stringify(f"${year.getValue}%+d"))
        }
      },
      test("YearMonth") {
        check(Gen.yearMonth) { yearMonth =>
          assertEncodes(
            Schema.Primitive(StandardType.YearMonthType),
            yearMonth,
            if (yearMonth.getYear > 9999) stringify(s"+${yearMonth.toString}")
            else stringify(yearMonth.toString),
          )
        }
      },
      test("ZoneDateTime") {
        check(Gen.zonedDateTime) { zonedDateTime =>
          assertEncodes(
            Schema.Primitive(StandardType.ZonedDateTimeType),
            zonedDateTime,
            stringify(zonedDateTime.toString),
          )
        }
      },
      test("ZoneOffset") {
        check(Gen.zoneOffset) { zoneOffset =>
          assertEncodes(Schema.Primitive(StandardType.ZoneOffsetType), zoneOffset, stringify(zoneOffset.toString))
        }
      },
      test("ZoneId") {
        check(Gen.zoneId) { zoneId =>
          assertEncodes(Schema.Primitive(StandardType.ZoneIdType), zoneId, stringify(zoneId.toString))
        }
      },
      test("Currency") {
        check(Gen.currency) { currency =>
          assertEncodes(Schema.Primitive(StandardType.CurrencyType), currency, stringify(currency.toString))
        }
      } @@ TestAspect.jvmOnly,
    ),
    suite("sequence")(
      test("of primitives") {
        check(Gen.chunkOf(Gen.string)) { chunk =>
          assertEncodes(
            Schema.chunk(Schema.Primitive(StandardType.StringType)),
            chunk,
            chunk.map(stringify).mkString("[", ",", "]"),
          )
        }
      },
      test("of complex values") {
        check(Gen.chunkOf(genValue)) { chunk =>
          assertEncodes(
            Schema.chunk(Value.schema),
            chunk,
            chunk.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
          )
        }
      },
    ),
    suite("non-empty sequence")(
      test("of primitives") {
        check(genNonEmptyChunkOf(Gen.string)) { nonEmptyChunk =>
          assertEncodes(
            Schema.nonEmptyChunk(Schema.Primitive(StandardType.StringType)),
            nonEmptyChunk,
            nonEmptyChunk.map(stringify).mkString("[", ",", "]"),
          )
        }
      },
      test("of complex values") {
        check(genNonEmptyChunkOf(genValue)) { nonEmptyChunk =>
          assertEncodes(
            Schema.nonEmptyChunk(Value.schema),
            nonEmptyChunk,
            nonEmptyChunk.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
          )
        }
      },
    ),
    suite("map")(
      test("of simple keys and values") {
        assertEncodes(
          Schema.map[Int, Value],
          Map(0 -> Value(0, true), 1 -> Value(1, false)),
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
        )
      },
      test("of simple keys and values where the key's schema is lazy") {
        assertEncodes(
          Schema.map[Int, Value](Schema.defer(Schema[Int]), Schema[Value]),
          Map(0 -> Value(0, true), 1 -> Value(1, false)),
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
        )
      },
      test("of complex keys and values") {
        assertEncodes(
          Schema.map[Key, Value],
          Map(Key("a", 0) -> Value(0, true), Key("b", 1) -> Value(1, false)),
          """[[{"name":"a","index":0},{"first":0,"second":true}],[{"name":"b","index":1},{"first":1,"second":false}]]""",
        )
      },
      test("of complex keys with transformation to primitive keys") {
        assertEncodes(
          Schema.map[KeyWrapper, ValueWrapper],
          Map(
            KeyWrapper("wrapped_key_1") -> ValueWrapper(value = "some_value"),
            KeyWrapper("wrapped_key_2") -> ValueWrapper(value = "some_other_value"),
          ),
          """{"wrapped_key_1":{"value":"some_value"},"wrapped_key_2":{"value":"some_other_value"}}""",
        )
      },
    ),
    suite("non-empty map")(
      test("of simple keys and values") {
        assertEncodes(
          Schema.nonEmptyMap[Int, Value],
          NonEmptyMap(0 -> Value(0, true), 1 -> Value(1, false)),
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
        )
      },
      test("of simple keys and values where the key's schema is lazy") {
        assertEncodes(
          Schema.nonEmptyMap[Int, Value](Schema.defer(Schema[Int]), Schema[Value]),
          NonEmptyMap(0 -> Value(0, true), 1 -> Value(1, false)),
          """{"0":{"first":0,"second":true},"1":{"first":1,"second":false}}""",
        )
      },
      test("of complex keys and values") {
        assertEncodes(
          Schema.nonEmptyMap[Key, Value],
          NonEmptyMap(Key("a", 0) -> Value(0, true), Key("b", 1) -> Value(1, false)),
          """[[{"name":"a","index":0},{"first":0,"second":true}],[{"name":"b","index":1},{"first":1,"second":false}]]""",
        )
      },
      test("of complex keys with transformation to primitive keys") {
        assertEncodes(
          Schema.nonEmptyMap[KeyWrapper, ValueWrapper],
          NonEmptyMap(
            KeyWrapper("wrapped_key_1") -> ValueWrapper(value = "some_value"),
            KeyWrapper("wrapped_key_2") -> ValueWrapper(value = "some_other_value"),
          ),
          """{"wrapped_key_1":{"value":"some_value"},"wrapped_key_2":{"value":"some_other_value"}}""",
        )
      },
    ) @@ ignore, // FIXME: find better test, NonEmptyMap ordering is non-deterministic
    suite("set")(
      test("of primitives") {
        check(Gen.setOf(Gen.string)) { set =>
          assertEncodes(
            Schema.set(Schema.Primitive(StandardType.StringType)),
            set,
            set.map(stringify).mkString("[", ",", "]"),
          )
        }
      },
      test("of complex values") {
        check(Gen.setOf(genValue)) { set =>
          assertEncodes(
            Schema.set(Value.schema),
            set,
            set.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
          )
        }
      },
    ) @@ ignore, // FIXME: find better test, Set ordering is non-deterministic
    suite("non-empty set")(
      test("of primitives") {
        check(genNonEmptySetOf(Gen.string)) { nonEmptySet =>
          assertEncodes(
            Schema.nonEmptySet(Schema.Primitive(StandardType.StringType)),
            nonEmptySet,
            nonEmptySet.map(stringify).mkString("[", ",", "]"),
          )
        }
      },
      test("of complex values") {
        check(genNonEmptySetOf(genValue)) { nonEmptySet =>
          assertEncodes(
            Schema.nonEmptySet(Value.schema),
            nonEmptySet,
            nonEmptySet.map { case Value(x, y) => s"""{"first":$x,"second":$y}""" }.mkString("[", ",", "]"),
            debug = true,
          )
        }
      },
    ) @@ ignore, // FIXME: find better test, NonEmptySet ordering is non-deterministic
    suite("transform")(
      test("of simple string to its size as int") {
        check(Gen.string) { string =>
          assertEncodes(
            Schema.Transform[Int, String, Unit](
              Schema[Int],
              _ => Left("undefined"),
              str => Right(str.size),
              Chunk.empty[Any],
              (),
            ),
            string,
            string.size.toString,
          )
        }
      },
      test("of failing as null") {
        check(Gen.int) { int =>
          assertEncodes(
            Schema.Transform[String, Int, Unit](
              Schema[String],
              str => Right(str.size),
              _ => Left("undefined"),
              Chunk.empty[Any],
              (),
            ),
            int,
            """null""",
          )
        }
      },
    ),
    suite("tuple")(
      test("of primitives") {
        check(Gen.string <*> Gen.int) { case (string, int) =>
          assertEncodes(
            Schema.Tuple2(
              Schema.Primitive(StandardType.StringType),
              Schema.Primitive(StandardType.IntType),
            ),
            (string, int),
            s"""[${stringify(string)},$int]""",
          )
        }
      },
      test("of complex values") {
        check(Gen.string <*> genValue) { case (string, value) =>
          assertEncodes(
            Schema.Tuple2(
              Schema.Primitive(StandardType.StringType),
              Value.schema,
            ),
            (string, value),
            s"""[${stringify(string)},${s"""{"first":${value.first},"second":${value.second}}"""}]""",
          )
        }
      },
    ),
    suite("optional")(
      test("of some primitive") {
        check(Gen.string) { string =>
          assertEncodes(Schema.Optional(Schema.Primitive(StandardType.StringType)), Some(string), stringify(string))
        }
      },
      test("of absent primitive") {
        assertEncodes(Schema.Optional(Schema.Primitive(StandardType.StringType)), None, "null")
      },
      test("of some complex value") {
        check(genValue) { value =>
          assertEncodes(
            Schema.Optional(Value.schema),
            Some(value),
            s"""{"first":${value.first},"second":${value.second}}""",
          )
        }
      },
      test("of absent complex value") {
        assertEncodes(Schema.Optional(Value.schema), None, "null")
      },
    ),
    suite("fail")(
      test("of any cause") {
        assertEncodes(Schema.Fail[Exception]("Failed"), new Exception("Failed"), """{}""")
      },
    ),
    suite("generic record")(
      test("skips transient fields if annotated with rejectExtraFields") {
        assertEncodes(
          RecordExample.schema.annotate(rejectExtraFields()),
          RecordExample(f1 = Some("test"), f3 = Some("transient")),
          """{"$f1":"test"}""".stripMargin,
        )
      },
    ),
    suite("either")(
      test("left") {
        check(Gen.int) { int =>
          assertEncodes(
            Schema.Either(Schema[Int], Schema[String]),
            Left(int),
            s"""{"Left":$int}""",
          )
        }
      },
      test("right") {
        check(Gen.string) { string =>
          assertEncodes(
            Schema.Either(Schema[Int], Schema[String]),
            Right(string),
            s"""{"Right":${stringify(string)}}""",
          )
        }
      },
    ),
    suite("fallback")(
      test("left") {
        check(Gen.int) { int =>
          assertEncodes(
            Schema.Fallback(Schema[Int], Schema[String]),
            Fallback.Left(int),
            int.toString,
          )
        }
      },
      test("right") {
        check(Gen.string) { string =>
          assertEncodes(
            Schema.Fallback(Schema[Int], Schema[String]),
            Fallback.Right(string),
            stringify(string),
          )
        }
      },
      test("both") {
        check(Gen.int <*> Gen.string) { case (int, string) =>
          assertEncodes(
            Schema.Fallback(Schema[Int], Schema[String]),
            Fallback.Both(int, string),
            s"""[$int,${stringify(string)}]""",
          )
        }
      },
    ),
    suite("lazy")(
      test("of primitive") {
        check(Gen.string) { string =>
          assertEncodes(
            Schema.defer(Schema[String]),
            string,
            stringify(string),
          )
        }
      },
      test("of complex values") {
        check(genValue) { value =>
          assertEncodes(
            Schema.defer(Value.schema),
            value,
            s"""{"first":${value.first},"second":${value.second}}""",
          )
        }
      },
    ),
    suite("record")(
      test("of primitives") {
        assertEncodes(
          recordSchema,
          ListMap[String, Any]("foo" -> "s", "bar" -> 1),
          """{"foo":"s","bar":1}""",
        )
      },
      test("of records") {
        assertEncodes(
          nestedRecordSchema,
          ListMap[String, Any]("l1" -> "s", "l2" -> ListMap("foo" -> "s", "bar" -> 1)),
          """{"l1":"s","l2":{"foo":"s","bar":1}}""",
        )
      },
      test("case class") {
        check(searchRequestGen) { request =>
          assertEncodes(
            searchRequestSchema,
            request,
            s"""{"query":"${request.query}","size":${request.size},"page":${request.page}""" +
              request.nextPage
                .map(x => s""","nextPage":${stringify(x)}}""")
                .getOrElse("""}"""),
          )
        }
      },
      test("case object") {
        assertEncodes(
          schemaObject,
          Singleton,
          "{}",
        )
      },
      test("record with option fields encoded as null") {
        assertEncodes(
          recordWithOptionSchema,
          ListMap[String, Any]("foo" -> Some("s"), "bar" -> None),
          """{"foo":"s","bar":null}""",
        )
      },
      test("case class with option fields omitted when empty") {
        assertEncodes(
          WithOptionFields.schema,
          WithOptionFields(Some("s"), None),
          """{"a":"s"}""",
        )
      },
      test("case class with backticked field name") {
        assertEncodes(
          Schema[BacktickedFieldName],
          BacktickedFieldName("test"),
          """{"x-api-key":"test"}""",
        )
      },
    ),
    suite("enumeration")(
      test("of primitives") {
        assertEncodes(
          enumSchema,
          "foo",
          """{"string":"foo"}""",
        )
      },
      test("ADT") {
        assertEncodes(
          Schema[Enumeration],
          Enumeration(StringValue("foo")),
          """{"oneOf":{"StringValue":{"value":"foo"}}}""",
        )
      },
      test("ADT with annotation") {
        assertEncodes(
          Schema[Enumeration2],
          Enumeration2(StringValue2("foo2")),
          """{"oneOf":{"_type":"StringValue2","value":"foo2"}}""",
        )
      },
      test("case class") {
        assertEncodes(
          searchRequestWithTransientFieldSchema,
          SearchRequestWithTransientField("foo", 10, 20, "bar"),
          """{"query":"foo","page":10,"size":20}""",
        )
      },
      test("case name annotation") {
        assertEncodes(
          PaymentMethod.schema,
          WireTransfer("foo", "bar"),
          """{"wire_transfer":{"accountNumber":"foo","bankCode":"bar"}}""",
        )
      },
      test("transient case annotation") {
        assertEncodes(
          PaymentMethod.schema,
          PayPal("foo@bar.com"),
          """{}""",
        )
      },
      test("case name annotation with discriminator") {
        assertEncodes(
          Subscription.schema,
          Recurring("monthly", 10),
          """{"type":"recurring","period":"monthly","amount":10}""",
        )
      },
      test("case name annotation with empty fields") {
        assertEncodes(
          Subscription.schema,
          Subscription.Unlimited(None),
          """{"type":"unlimited"}""",
        )
      },
      suite("with no discriminator")(
        test("example 1") {
          assertEncodes(
            Prompt.schema,
            Prompt.Single("hello"),
            """{"value":"hello"}""",
          )
        },
        test("example 2") {
          assertEncodes(
            Prompt.schema,
            Prompt.Multiple(List("hello", "world")),
            """{"value":["hello","world"]}""",
          )
        },
      ),
      test("respects the case name annotation") {
        assertEncodes(
          Enum23Cases.schema,
          Enum23Cases.Case1("foo"),
          """{"NumberOne":{"value":"foo"}}""",
        )
      },
    ),
    suite("dynamic direct mapping")(
      test("record") {
        assertEncodes(
          Schema.dynamicValue.annotate(directDynamicMapping()),
          DynamicValue.Record(
            TypeId.Structural,
            ListMap(
              "foo" -> DynamicValue.Primitive("s", StandardType.StringType),
              "bar" -> DynamicValue.Primitive(1, StandardType.IntType),
            ),
          ),
          """{"foo":"s","bar":1}""",
        )
      },
    ),
    suite("optional field annotation")(
      test("list empty") {
        assertEncodes(
          Schema[WithOptField],
          WithOptField(Nil, Map("foo" -> 1)),
          """{"map":{"foo":1}}""",
        )
      },
      test("map empty") {
        assertEncodes(
          Schema[WithOptField],
          WithOptField(List("foo"), Map.empty),
          """{"list":["foo"]}""",
        )
      },
      test("all empty") {
        assertEncodes(
          Schema[WithOptField],
          WithOptField(Nil, Map.empty),
          """{}""",
        )
      },
    ),
    suite("empty collections config")(
      test("list empty") {
        assertEncodes(
          Schema[ListAndMapAndOption],
          ListAndMapAndOption(Nil, Map("foo" -> 1), Some("foo")),
          """{"map":{"foo":1},"option":"foo"}""",
          IgnoreEmptyCollectionsConfig,
        )
      },
      test("map empty") {
        assertEncodes(
          Schema[ListAndMapAndOption],
          ListAndMapAndOption(List("foo"), Map.empty, Some("foo")),
          """{"list":["foo"],"option":"foo"}""",
          IgnoreEmptyCollectionsConfig,
        )
      },
      test("option empty") {
        assertEncodes(
          Schema[ListAndMapAndOption],
          ListAndMapAndOption(List("foo"), Map("foo" -> 1), None),
          """{"list":["foo"],"map":{"foo":1}}""",
          IgnoreEmptyCollectionsConfig,
        )
      },
      test("all empty") {
        assertEncodes(
          Schema[ListAndMapAndOption],
          ListAndMapAndOption(Nil, Map.empty, None),
          """{}""",
          IgnoreEmptyCollectionsConfig,
        )
      },
      test("all empty, but don't ignore empty collections") {
        assertEncodes(
          Schema[ListAndMapAndOption],
          ListAndMapAndOption(Nil, Map.empty, None),
          """{"list":[],"map":{},"option":null}""",
          KeepNullsAndEmptyColleciontsConfig,
        )
      },
    ),
    suite("streams")(
      suite("of integers")(
        test("Encodes a stream with multiple integers") {
          assertEncodesMany(Schema[Int], 1 to 5, "1\n2\n3\n4\n5")
        },
        test("Encodes a stream with multiple integers to an array") {
          assertEncodesMany(
            Schema[Int],
            1 to 5,
            "[1,2,3,4,5]",
            StreamingConfig,
          )
        },
      ),
      suite("Streams of booleans")(
        test("Encodes a stream with multiple booleans") {
          assertEncodesMany(Schema[Boolean], List(true, true, false), "true\ntrue\nfalse")
        },
        test("Encodes a stream with multiple booleans to an array") {
          assertEncodesMany(
            Schema[Boolean],
            List(true, true, false),
            "[true,true,false]",
            StreamingConfig,
          )
        },
      ),
      suite("Streams of strings")(
        test("Encodes a stream with multiple strings") {
          assertEncodesMany(Schema[String], List("a", "b", "c"), "\"a\"\n\"b\"\n\"c\"")
        },
        test("Encodes a stream with multiple strings as an array") {
          assertEncodesMany(
            Schema[String],
            List("a", "b", "c"),
            "[\"a\",\"b\",\"c\"]",
            StreamingConfig,
          )
        },
      ),
      suite("Stream of records")(
        test("Encodes a stream with multiple records") {
          assertEncodesMany(
            Person.schema,
            List(
              Person("Alice", 1),
              Person("Bob", 2),
              Person("Charlie", 3),
            ),
            """{"name":"Alice","age":1}
              |{"name":"Bob","age":2}
              |{"name":"Charlie","age":3}""".stripMargin,
          )
        },
        test("Encodes a stream with multiple records as an array") {
          assertEncodesMany(
            Person.schema,
            List(
              Person("Alice", 1),
              Person("Bob", 2),
              Person("Charlie", 3),
            ),
            """[{"name":"Alice","age":1},{"name":"Bob","age":2},{"name":"Charlie","age":3}]""",
            StreamingConfig,
          )
        },
        test("Encodes a stream with no records") {
          assertEncodesMany(
            Person.schema,
            List.empty[Person],
            "",
          )
        },
        test("Encodes a stream with no records") {
          assertEncodesMany(
            Person.schema,
            List.empty[Person],
            "[]",
            StreamingConfig,
          )
        },
      ),
    ),
  )
}
