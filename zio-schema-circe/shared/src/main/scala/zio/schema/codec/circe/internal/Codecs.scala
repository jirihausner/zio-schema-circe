package zio.schema.codec.circe.internal

import io.circe._
import zio.prelude.NonEmptyMap
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.circe.CirceCodec
import zio.{Chunk, ChunkBuilder}

import java.time.temporal.Temporal
import scala.collection.immutable.ListMap

private[circe] object Codecs extends Codecs

private[circe] trait Codecs {

  type DiscriminatorTuple = Chunk[(discriminatorName, String)]

  def encodeChunk[A](implicit encoder: Encoder[A]): Encoder.AsArray[Chunk[A]] =
    Encoder.AsArray.instance[Chunk[A]](_.map(encoder(_)).toVector)

  def decodeChunk[A](implicit decoder: Decoder[A]): Decoder[Chunk[A]] =
    Decoder.decodeVector(decoder).map(Chunk.fromIterable)

  def decodeFail[A](message: String): Decoder[A] = new Decoder[A] {
    override def apply(c: HCursor): Decoder.Result[A] = Left(DecodingFailure(message, c.history))
  }

  protected def parseJavaTime[A](f: String => A, s: String): Either[String, A] = {
    try {
      Right(f(s))
    } catch {
      case dte: java.time.DateTimeException => Left(s"$s is not a valid ISO-8601 format: ${dte.getMessage}")
      case ex: Exception                    =>
        Left(ex.getMessage)
    }
  }

  def decodeCurrency: Decoder[java.util.Currency] = Decoder.decodeString.emap { str =>
    try {
      Right(java.util.Currency.getInstance(str))
    } catch {
      case iae: IllegalArgumentException => Left(s"$str is not a valid currency: ${iae.getMessage}")
    }
  }

  // fixes inconsistencies introduced by original circe encoder (dropping trailing zeroes etc.)
  private def encodeJavaTime[T <: Temporal]: Encoder[T] = Encoder.encodeString.contramap(_.toString)

  def encodePrimitive[A](standardType: StandardType[A]): Encoder[A] = standardType match {
    case StandardType.UnitType           => Encoder.encodeUnit
    case StandardType.StringType         => Encoder.encodeString
    case StandardType.BoolType           => Encoder.encodeBoolean
    case StandardType.ByteType           => Encoder.encodeByte
    case StandardType.ShortType          => Encoder.encodeShort
    case StandardType.IntType            => Encoder.encodeInt
    case StandardType.LongType           => Encoder.encodeLong
    case StandardType.FloatType          => Encoder.encodeFloat
    case StandardType.DoubleType         => Encoder.encodeDouble
    case StandardType.BinaryType         => encodeChunk(Encoder.encodeByte)
    case StandardType.CharType           => Encoder.encodeChar
    case StandardType.BigIntegerType     => Encoder.encodeBigInt.contramap[java.math.BigInteger](new BigInt(_))
    case StandardType.BigDecimalType     => Encoder.encodeBigDecimal.contramap[java.math.BigDecimal](new BigDecimal(_))
    case StandardType.UUIDType           => Encoder.encodeUUID
    case StandardType.DayOfWeekType      => Encoder.encodeString.contramap[java.time.DayOfWeek](_.toString)
    case StandardType.DurationType       => Encoder.encodeDuration
    case StandardType.InstantType        => Encoder.encodeInstant
    case StandardType.LocalDateType      => Encoder.encodeLocalDate
    case StandardType.LocalDateTimeType  => encodeJavaTime[java.time.LocalDateTime]
    case StandardType.LocalTimeType      => encodeJavaTime[java.time.LocalTime]
    case StandardType.MonthType          => Encoder.encodeString.contramap[java.time.Month](_.toString)
    case StandardType.MonthDayType       => Encoder.encodeMonthDay
    case StandardType.OffsetDateTimeType => encodeJavaTime[java.time.OffsetDateTime]
    case StandardType.OffsetTimeType     => encodeJavaTime[java.time.OffsetTime]
    case StandardType.PeriodType         => Encoder.encodePeriod
    case StandardType.YearType           => Encoder.encodeYear
    case StandardType.YearMonthType      => Encoder.encodeYearMonth
    case StandardType.ZonedDateTimeType  => encodeJavaTime[java.time.ZonedDateTime]
    case StandardType.ZoneIdType         => Encoder.encodeZoneId
    case StandardType.ZoneOffsetType     => Encoder.encodeZoneOffset
    case StandardType.CurrencyType       => Encoder.encodeString.contramap[java.util.Currency](_.toString)
  }

  def decodePrimitive[A](standardType: StandardType[A]): Decoder[A] = standardType match {
    case StandardType.UnitType           => Decoder.decodeUnit
    case StandardType.StringType         => Decoder.decodeString
    case StandardType.BoolType           => Decoder.decodeBoolean
    case StandardType.ByteType           => Decoder.decodeByte
    case StandardType.ShortType          => Decoder.decodeShort
    case StandardType.IntType            => Decoder.decodeInt
    case StandardType.LongType           => Decoder.decodeLong
    case StandardType.FloatType          => Decoder.decodeFloat
    case StandardType.DoubleType         => Decoder.decodeDouble
    case StandardType.BinaryType         => decodeChunk(Decoder.decodeByte)
    case StandardType.CharType           => Decoder.decodeChar
    case StandardType.BigIntegerType     => Decoder.decodeBigInt.map(_.underlying)
    case StandardType.BigDecimalType     => Decoder.decodeBigDecimal.map(_.underlying)
    case StandardType.UUIDType           => Decoder.decodeUUID
    case StandardType.DayOfWeekType      => Decoder.decodeString.emap(parseJavaTime(java.time.DayOfWeek.valueOf, _))
    case StandardType.DurationType       => Decoder.decodeDuration
    case StandardType.InstantType        => Decoder.decodeInstant
    case StandardType.LocalDateType      => Decoder.decodeLocalDate
    case StandardType.LocalDateTimeType  => Decoder.decodeLocalDateTime
    case StandardType.LocalTimeType      => Decoder.decodeLocalTime
    case StandardType.MonthType          => Decoder.decodeString.emap(parseJavaTime(java.time.Month.valueOf, _))
    case StandardType.MonthDayType       => Decoder.decodeMonthDay
    case StandardType.OffsetDateTimeType => Decoder.decodeOffsetDateTime
    case StandardType.OffsetTimeType     => Decoder.decodeOffsetTime
    case StandardType.PeriodType         => Decoder.decodePeriod
    case StandardType.YearType           => Decoder.decodeYear
    case StandardType.YearMonthType      => Decoder.decodeYearMonth
    case StandardType.ZonedDateTimeType  => Decoder.decodeZonedDateTime
    case StandardType.ZoneIdType         => Decoder.decodeZoneId
    case StandardType.ZoneOffsetType     => Decoder.decodeZoneOffset
    case StandardType.CurrencyType       => decodeCurrency
  }

  def encodeSchema[A](
    schema: Schema[A],
    config: CirceCodec.Config,
    discriminatorTuple: DiscriminatorTuple = Chunk.empty,
  ): Encoder[A] = schema match {
    case Schema.Primitive(standardType, _)                     => encodePrimitive(standardType)
    case Schema.Sequence(schema, _, g, _, _)                   =>
      encodeChunk(encodeSchema(schema, config, discriminatorTuple)).contramap(g)
    case Schema.NonEmptySequence(schema, _, g, _, _)           =>
      encodeChunk(encodeSchema(schema, config, discriminatorTuple)).contramap(g)
    case Schema.Map(ks, vs, _)                                 => encodeMap(ks, vs, config, discriminatorTuple)
    case Schema.NonEmptyMap(ks: Schema[kt], vs: Schema[vt], _) =>
      encodeMap(ks, vs, config, discriminatorTuple)
        .contramap[NonEmptyMap[kt, vt]](_.toMap.asInstanceOf[Map[kt, vt]])
        .asInstanceOf[Encoder[A]]
    case Schema.Set(s, _)                                      =>
      encodeChunk(encodeSchema(s, config, discriminatorTuple)).contramap(m => Chunk.fromIterable(m))
    case Schema.Transform(c, _, g, a, _)       => encodeTransform(a.foldLeft(c)((s, a) => s.annotate(a)), g, config)
    case Schema.Tuple2(l, r, _)                =>
      Encoder.encodeTuple2(encodeSchema(l, config, discriminatorTuple), encodeSchema(r, config, discriminatorTuple))
    case Schema.Optional(schema, _)            => Encoder.encodeOption(encodeSchema(schema, config, discriminatorTuple))
    case Schema.Fail(_, _)                     => Encoder.encodeUnit.contramap(_ => ())
    case Schema.GenericRecord(_, structure, _) => encodeRecord(structure.toChunk, config)
    case Schema.Either(left, right, _)         =>
      Encoder.encodeEither("Left", "Right")(
        encodeSchema(left, config, discriminatorTuple),
        encodeSchema(right, config, discriminatorTuple),
      )
    case Schema.Fallback(left, right, _, _)    =>
      encodeFallback(encodeSchema(left, config, discriminatorTuple), encodeSchema(right, config, discriminatorTuple))
    case l @ Schema.Lazy(_)                    => encodeSchema(l.schema, config, discriminatorTuple)
    case s: Schema.Record[A]                   => encodeCaseClass(s, config, discriminatorTuple)
    // scalafmt: { maxColumn = 400, optIn.configStyleArguments = false }
    case e @ Schema.Enum1(_, c, _)             => encodeEnum(e, config, c)
    case e @ Schema.Enum2(_, c1, c2, _)        => encodeEnum(e, config, c1, c2)
    case e @ Schema.Enum3(_, c1, c2, c3, _)    => encodeEnum(e, config, c1, c2, c3)
    case e @ Schema.Enum4(_, c1, c2, c3, c4, _)                                                                                       => encodeEnum(e, config, c1, c2, c3, c4)
    case e @ Schema.Enum5(_, c1, c2, c3, c4, c5, _)                                                                                   => encodeEnum(e, config, c1, c2, c3, c4, c5)
    case e @ Schema.Enum6(_, c1, c2, c3, c4, c5, c6, _)                                                                               => encodeEnum(e, config, c1, c2, c3, c4, c5, c6)
    case e @ Schema.Enum7(_, c1, c2, c3, c4, c5, c6, c7, _)                                                                           => encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7)
    case e @ Schema.Enum8(_, c1, c2, c3, c4, c5, c6, c7, c8, _)                                                                       => encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8)
    case e @ Schema.Enum9(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, _)                                                                   => encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9)
    case e @ Schema.Enum10(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, _)                                                             =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10)
    case e @ Schema.Enum11(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, _)                                                        =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11)
    case e @ Schema.Enum12(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, _)                                                   =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12)
    case e @ Schema.Enum13(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, _)                                              =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13)
    case e @ Schema.Enum14(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, _)                                         =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14)
    case e @ Schema.Enum15(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, _)                                    =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15)
    case e @ Schema.Enum16(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, _)                               =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16)
    case e @ Schema.Enum17(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, _)                          =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17)
    case e @ Schema.Enum18(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, _)                     =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18)
    case e @ Schema.Enum19(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, _)                =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19)
    case e @ Schema
          .Enum20(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, _) =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20)
    case e @ Schema
          .Enum21(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, _) =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21)
    case e @ Schema.Enum22(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22, _) =>
      encodeEnum(e, config, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22)
    // scalafmt: { maxColumn = 120, optIn.configStyleArguments = true }
    case e @ Schema.EnumN(_, cs, _) => encodeEnum(e, config, cs.toSeq: _*)
    case d @ Schema.Dynamic(_)      => encodeDynamic(d, config)
    case null                       =>
      throw new Exception(s"A captured schema is null, most likely due to wrong field initialization order")
  }

  def decodeSchema[A](schema: Schema[A], discriminator: Int = -1): Decoder[A] = schema match {
    case Schema.Primitive(standardType, _)              => decodePrimitive(standardType)
    case Schema.Optional(codec, _)                      => Decoder.decodeOption(decodeSchema(codec, discriminator))
    case Schema.Tuple2(left, right, _)                  =>
      Decoder.decodeTuple2(decodeSchema(left, -1), decodeSchema(right, -1))
    case Schema.Transform(c, f, _, a, _)                =>
      decodeSchema(a.foldLeft(c)((s, a) => s.annotate(a)), discriminator).emap(f)
    case Schema.Sequence(codec, f, _, _, _)             => decodeChunk(decodeSchema(codec, -1)).map(f)
    case s @ Schema.NonEmptySequence(codec, _, _, _, _) =>
      decodeChunk(decodeSchema(codec, -1)).map(s.fromChunk)
    case Schema.Map(ks, vs, _)                          => decodeMap(ks, vs)
    case Schema.NonEmptyMap(ks, vs, _)                  =>
      decodeMap(ks, vs).emap(m => NonEmptyMap.fromMapOption(m).toRight("NonEmptyMap expected"))
    case Schema.Set(s, _)                              => decodeChunk(decodeSchema(s, -1)).map(entries => entries.toSet)
    case Schema.Fail(message, _)                       => decodeFail(message)
    case Schema.GenericRecord(_, structure, _)         =>
      decodeRecord(structure.toChunk, schema.annotations.contains(rejectExtraFields()))
    case Schema.Either(left, right, _)                 =>
      Decoder.decodeEither("Left", "Right")(decodeSchema(left, -1), decodeSchema(right, -1))
    case s @ Schema.Fallback(_, _, _, _)               => decodeFallback(s)
    case l @ Schema.Lazy(_)                            => decodeSchema(l.schema, discriminator)
    case s @ Schema.CaseClass0(_, _, _)                => decodeCaseClass0(s)
    case s @ Schema.CaseClass1(_, _, _, _)             => decodeCaseClass1(s, discriminator)
    case s @ Schema.CaseClass2(_, _, _, _, _)          => decodeCaseClass2(s, discriminator)
    case s @ Schema.CaseClass3(_, _, _, _, _, _)       => decodeCaseClass3(s, discriminator)
    case s @ Schema.CaseClass4(_, _, _, _, _, _, _)    => decodeCaseClass4(s, discriminator)
    case s @ Schema.CaseClass5(_, _, _, _, _, _, _, _) => decodeCaseClass5(s, discriminator)
    case s @ Schema.CaseClass6(_, _, _, _, _, _, _, _, _)                       => decodeCaseClass6(s, discriminator)
    case s @ Schema.CaseClass7(_, _, _, _, _, _, _, _, _, _)                    => decodeCaseClass7(s, discriminator)
    case s @ Schema.CaseClass8(_, _, _, _, _, _, _, _, _, _, _)                 => decodeCaseClass8(s, discriminator)
    case s @ Schema.CaseClass9(_, _, _, _, _, _, _, _, _, _, _, _)              => decodeCaseClass9(s, discriminator)
    case s @ Schema.CaseClass10(_, _, _, _, _, _, _, _, _, _, _, _, _)          => decodeCaseClass10(s, discriminator)
    case s @ Schema.CaseClass11(_, _, _, _, _, _, _, _, _, _, _, _, _, _)       =>
      decodeCaseClass11(s, discriminator)
    case s @ Schema.CaseClass12(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _)    =>
      decodeCaseClass12(s, discriminator)
    case s @ Schema.CaseClass13(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass13(s, discriminator)
    case s @ Schema
          .CaseClass14(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass14(s, discriminator)
    case s @ Schema
          .CaseClass15(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass15(s, discriminator)
    case s @ Schema.CaseClass16(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass16(s, discriminator)
    case s @ Schema.CaseClass17(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)                       =>
      decodeCaseClass17(s, discriminator)
    case s @ Schema.CaseClass18(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)                    =>
      decodeCaseClass18(s, discriminator)
    case s @ Schema.CaseClass19(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)                 =>
      decodeCaseClass19(s, discriminator)
    case s @ Schema.CaseClass20(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)                 =>
      decodeCaseClass20(s, discriminator)
    case s @ Schema.CaseClass21(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)                 =>
      decodeCaseClass21(s, discriminator)
    case s @ Schema.CaseClass22(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)                 =>
      decodeCaseClass22(s, discriminator)
    // scalafmt: { maxColumn = 400, optIn.configStyleArguments = false }
    case e @ Schema.Enum1(_, c, _)                                                                                => decodeEnum(e, c)
    case e @ Schema.Enum2(_, c1, c2, _)                                                                           => decodeEnum(e, c1, c2)
    case e @ Schema.Enum3(_, c1, c2, c3, _)                                                                       => decodeEnum(e, c1, c2, c3)
    case e @ Schema.Enum4(_, c1, c2, c3, c4, _)                                                                   => decodeEnum(e, c1, c2, c3, c4)
    case e @ Schema.Enum5(_, c1, c2, c3, c4, c5, _)                                                               => decodeEnum(e, c1, c2, c3, c4, c5)
    case e @ Schema.Enum6(_, c1, c2, c3, c4, c5, c6, _)                                                           => decodeEnum(e, c1, c2, c3, c4, c5, c6)
    case e @ Schema.Enum7(_, c1, c2, c3, c4, c5, c6, c7, _)                                                       => decodeEnum(e, c1, c2, c3, c4, c5, c6, c7)
    case e @ Schema.Enum8(_, c1, c2, c3, c4, c5, c6, c7, c8, _)                                                   => decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8)
    case e @ Schema.Enum9(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, _)                                               =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9)
    case e @ Schema.Enum10(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, _)                                         =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10)
    case e @ Schema.Enum11(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, _)                                    =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11)
    case e @ Schema.Enum12(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, _)                               =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12)
    case e @ Schema.Enum13(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, _)                          =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13)
    case e @ Schema.Enum14(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, _)                     =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14)
    case e @ Schema.Enum15(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, _)                =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15)
    case e @ Schema.Enum16(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, _)           =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16)
    case e @ Schema.Enum17(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, _)      =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17)
    case e @ Schema.Enum18(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, _) =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18)
    case e @ Schema.Enum19(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, _)                =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19)
    case e @ Schema
          .Enum20(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, _) =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20)
    case e @ Schema.Enum21(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, _)      =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21)
    case e @ Schema.Enum22(_, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22, _) =>
      decodeEnum(e, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21, c22)
    // scalafmt: { maxColumn = 120, optIn.configStyleArguments = true }
    case e @ Schema.EnumN(_, cs, _) => decodeEnum(e, cs.toSeq: _*)
    case d @ Schema.Dynamic(_)      => decodeDynamic(d)
    case _ => throw new Exception(s"Missing a handler for decoding of schema ${schema.toString()}.")
  }

  def encodeField[B](schema: Schema[B]): Option[KeyEncoder[B]] = schema match {
    case Schema.Primitive(StandardType.StringType, _) => Some(KeyEncoder.encodeKeyString)
    case Schema.Primitive(StandardType.LongType, _)   => Some(KeyEncoder.encodeKeyLong)
    case Schema.Primitive(StandardType.IntType, _)    => Some(KeyEncoder.encodeKeyInt)
    case Schema.Transform(c, _, g, a, _)              =>
      encodeField(a.foldLeft(c)((s, a) => s.annotate(a))).map { encoder =>
        new KeyEncoder[B] {
          override def apply(b: B): String = g(b) match {
            case Left(reason) => throw new RuntimeException(s"Failed to encode field $b: $reason")
            case Right(field) => encoder(field)
          }
        }
      }
    case Schema.Lazy(inner)                           => encodeField(inner())
    case _                                            => None
  }

  def decodeField[A](schema: Schema[A]): Option[KeyDecoder[A]] = schema match {
    case Schema.Primitive(StandardType.StringType, _) => Some(KeyDecoder.decodeKeyString)
    case Schema.Primitive(StandardType.LongType, _)   => Some(KeyDecoder.decodeKeyLong)
    case Schema.Primitive(StandardType.IntType, _)    => Some(KeyDecoder.decodeKeyInt)
    case Schema.Transform(c, f, _, a, _)              =>
      decodeField(a.foldLeft(c)((s, a) => s.annotate(a))).map { decoder =>
        decoder.map { key =>
          f(key) match {
            case Left(reason) => throw new RuntimeException(s"Failed to decode field $a: $reason")
            case Right(a)     => a
          }
        }
      }
    case Schema.Lazy(inner)                           => decodeField(inner())
    case _                                            => None
  }

  def encodeMap[K, V](
    ks: Schema[K],
    vs: Schema[V],
    config: CirceCodec.Config,
    discriminatorTuple: DiscriminatorTuple,
  ): Encoder[Map[K, V]] = encodeField(ks) match {
    case Some(keyEncoder) => Encoder.encodeMap(keyEncoder, encodeSchema(vs, config))
    case None             =>
      encodeChunk(
        Encoder.encodeTuple2(encodeSchema(ks, config, discriminatorTuple), encodeSchema(vs, config, discriminatorTuple)),
      )
        .contramap[Map[K, V]](Chunk.fromIterable)
  }

  def decodeMap[K, V](ks: Schema[K], vs: Schema[V]): Decoder[Map[K, V]] = decodeField(ks) match {
    case Some(keyDecoder) => Decoder.decodeMap(keyDecoder, decodeSchema(vs))
    case None             =>
      decodeChunk(
        Decoder
          .decodeTuple2(decodeSchema(ks), decodeSchema(vs)),
      )
        .map[Map[K, V]](_.toMap)
  }

  private def isEmptyJsonArray(json: Json): Boolean = json.asArray.map(_.isEmpty).getOrElse(false)

  def encodeDynamic(schema: Schema.Dynamic, config: CirceCodec.Config): Encoder[DynamicValue] = {
    val directMapping = schema.annotations.exists {
      case directDynamicMapping() => true
      case _                      => false
    }

    if (directMapping) {
      new Encoder[DynamicValue] { encoder =>
        override def apply(a: DynamicValue): Json = a match {
          case DynamicValue.Record(_, values)              =>
            val fields = values.map { case (k, v) =>
              val json = encoder(v)
              if (
                (!config.ignoreEmptyCollections || !isEmptyJsonArray(json)) &&
                (!config.ignoreNullValues || !json.isNull)
              ) Some(k -> json)
              else None
            }
            Json.obj(fields.collect { case Some(kv) => kv }.toSeq: _*)
          case DynamicValue.Enumeration(_, _)              =>
            throw new Exception(s"DynamicValue.Enumeration is not supported in directDynamicMapping mode")
          case DynamicValue.Sequence(values)               =>
            val maybeValues = values.map { case v =>
              val json = encoder(v)
              if (
                (!config.ignoreEmptyCollections || !isEmptyJsonArray(json)) &&
                (!config.ignoreNullValues || !json.isNull)
              ) Some(json)
              else None
            }
            Json.arr(maybeValues.flatten: _*)
          case DynamicValue.Dictionary(_)                  =>
            throw new Exception(s"DynamicValue.Dictionary is not supported in directDynamicMapping mode")
          case DynamicValue.SetValue(values)               =>
            val maybeValues = Chunk.fromIterable(values).map { case v =>
              val json = encoder(v)
              if (
                (!config.ignoreEmptyCollections || !isEmptyJsonArray(json)) &&
                (!config.ignoreNullValues || !json.isNull)
              ) Some(json)
              else None
            }
            Json.arr(maybeValues.flatten: _*)
          case DynamicValue.Primitive(value, standardType) => encodePrimitive(standardType)(value)
          case DynamicValue.Singleton(_)                   => Json.obj()
          case DynamicValue.SomeValue(value)               => encoder(value)
          case DynamicValue.NoneValue                      => Json.Null
          case DynamicValue.Tuple(_, _)                    =>
            throw new Exception(s"DynamicValue.Tuple is not supported in directDynamicMapping mode")
          case DynamicValue.LeftValue(_)                   =>
            throw new Exception(s"DynamicValue.LeftValue is not supported in directDynamicMapping mode")
          case DynamicValue.RightValue(_)                  =>
            throw new Exception(s"DynamicValue.RightValue is not supported in directDynamicMapping mode")
          case DynamicValue.BothValue(left, right)         =>
            Encoder.encodeTuple2(encoder, encoder)((left, right))
          case DynamicValue.DynamicAst(_)                  =>
            throw new Exception(s"DynamicValue.DynamicAst is not supported in directDynamicMapping mode")
          case DynamicValue.Error(message)                 =>
            throw new Exception(message)
        }
      }
    } else encodeSchema(DynamicValue.schema, config)
  }

  def decodeDynamic(schema: Schema.Dynamic): Decoder[DynamicValue] = {
    val directMapping = schema.annotations.exists {
      case directDynamicMapping() => true
      case _                      => false
    }

    if (directMapping) Decoder.decodeJson.map(zio.schema.codec.circe.fromJson)
    else decodeSchema(DynamicValue.schema)
  }

  def encodeTransform[A, B](
    schema: Schema[A],
    g: B => Either[String, A],
    config: CirceCodec.Config,
  ): Encoder[B] = new Encoder[B] {
    override def apply(b: B): Json = g(b) match {
      case Left(_)  => Json.Null
      case Right(a) => encodeSchema(schema, config)(a)
    }
  }

  def encodeEnum[Z](
    schema: Schema.Enum[Z],
    config: CirceCodec.Config,
    cases: Schema.Case[Z, _]*,
  ): Encoder[Z] = {
    // if all cases are CaseClass0, encode as a String
    if (schema.annotations.exists(_.isInstanceOf[simpleEnum])) {
      Encoder.encodeString.contramap {
        schema.nonTransientCases.map { case_ =>
          case_.schema.asInstanceOf[Schema.CaseClass0[Z]].defaultConstruct() ->
            case_.caseName
        }.toMap
      }
    } else {
      new Encoder[Z] {
        override def apply(value: Z): Json = {
          val nonTransientCase = {
            try
              cases.collectFirst {
                case c @ Schema.Case(_, _, _, _, _, annotations) if annotations.collectFirst { case _: transientCase =>
                      ()
                    }.isEmpty && c.deconstructOption(value).isDefined =>
                  c
              }
            catch {
              case ex: Throwable => throw new RuntimeException(s"Failed to encode enum type $schema", ex)
            }
          }

          nonTransientCase match {
            case None        => Json.obj()
            case Some(case_) =>
              val caseName = case_.caseName

              val discriminatorChunk = schema.annotations.collect { case d: discriminatorName =>
                (d, caseName)
              }
              val noDiscriminators   = schema.noDiscriminator

              val enumJson = encodeSchema(
                case_.schema.asInstanceOf[Schema[Any]],
                config,
                discriminatorTuple = if (noDiscriminators) Chunk.empty else discriminatorChunk,
              )(case_.deconstruct(value))

              if (discriminatorChunk.nonEmpty || noDiscriminators) enumJson
              else Json.obj(KeyEncoder.encodeKeyString(caseName) -> enumJson)
          }
        }
      }
    }
  }

  private def deAliasCaseName(alias: String, caseNameAliases: Map[String, String]): String =
    caseNameAliases.getOrElse(alias, alias)

  def decodeEnum[Z](parentSchema: Schema.Enum[Z], cases: Schema.Case[Z, _]*): Decoder[Z] = {
    val caseNameAliases = cases.flatMap { case Schema.Case(name, _, _, _, _, annotations) =>
      annotations.flatMap {
        case a: caseNameAliases => a.aliases.toList.map(_ -> name)
        case cn: caseName       => List(cn.name -> name)
        case _                  => Nil
      }
    }.toMap

    // if all cases are CaseClass0, decode as String
    if (cases.forall(_.schema.isInstanceOf[Schema.CaseClass0[_]])) {
      val caseMap: Map[String, Z] =
        cases.map(case_ => case_.id -> case_.schema.asInstanceOf[Schema.CaseClass0[Z]].defaultConstruct()).toMap
      Decoder.decodeString.emap { str =>
        caseMap.get(caseNameAliases.getOrElse(str, str)) match {
          case Some(z) => Right(z)
          case None    => Left("Unrecognized string")
        }
      }
    } else {

      val noDiscriminators = parentSchema.annotations.exists {
        case noDiscriminator() => true
        case _                 => false
      }

      new Decoder[Z] {
        override def apply(c: HCursor): Decoder.Result[Z] = {
          if (noDiscriminators) {
            cases.foldLeft[Option[Z]](None) {
              case (result @ Some(_), _) => result
              case (_, case_)            =>
                decodeSchema(case_.schema)(c).toOption.map(_.asInstanceOf[Z])
            } match {
              case Some(value) => Right(value)
              case None        => Left(DecodingFailure("None of the subtypes could decode the data", c.history))
            }
          } else {
            val maybeDiscriminatorName =
              parentSchema.annotations.collectFirst { case d: discriminatorName => d.tag }

            maybeDiscriminatorName match {
              case None =>
                c.keys.flatMap(_.headOption) match {
                  case Some(field) =>
                    val subtype = deAliasCaseName(field, caseNameAliases)
                    cases.find(_.id == subtype) match {
                      case Some(case_) =>
                        c.downField(field).success match {
                          case None             => Left(DecodingFailure("Unrecognized subtype", c.history))
                          case Some(caseCursor) =>
                            decodeSchema(case_.schema)(caseCursor).map(_.asInstanceOf[Z])
                        }
                      case None        => Left(DecodingFailure("Unrecognized subtype", c.history))
                    }
                  case None        =>
                    Left(DecodingFailure("Unrecognized subtype", c.history))
                }

              case Some(discriminator) =>
                c.keys.getOrElse(Iterable.empty).zipWithIndex.collectFirst[Either[DecodingFailure, (Int, String)]] {
                  case (name, index) if deAliasCaseName(name, caseNameAliases) == discriminator =>
                    c.downField(name).as[String] match {
                      case Left(error)  => Left(error)
                      case Right(value) =>
                        Right((index, deAliasCaseName(value, caseNameAliases)))
                    }
                } match {
                  case None                                  => Left(DecodingFailure("Unrecognized subtype", c.history))
                  case Some(Left(error))                     => Left(error)
                  case Some(Right((discriminator, subtype))) =>
                    cases.find(_.id == subtype) match {
                      case Some(case_) =>
                        decodeSchema(case_.schema, discriminator)(c).map(_.asInstanceOf[Z])
                      case None        =>
                        Left(DecodingFailure("Unrecognized subtype", c.history))
                    }
                }
            }
          }
        }
      }
    }
  }

  def encodeFallback[A, B](leftEncoder: Encoder[A], rightEncoder: Encoder[B]): Encoder[Fallback[A, B]] = {
    Encoder.instance[Fallback[A, B]] {
      case Fallback.Left(a)    => leftEncoder(a)
      case Fallback.Right(b)   => rightEncoder(b)
      case Fallback.Both(a, b) =>
        Encoder.encodeTuple2(leftEncoder, rightEncoder)((a, b))
    }
  }

  def decodeFallback[A, B](schema: Schema.Fallback[A, B]): Decoder[Fallback[A, B]] = new Decoder[Fallback[A, B]] {
    override def apply(c: HCursor) = {
      if (c.value.isArray) {
        c.values.toList.flatten match {
          case Nil                  => Left(DecodingFailure("Empty array on fallback", c.history))
          case left :: right :: Nil =>
            left.as(decodeSchema(schema.left)) match {
              case Left(_)      => right.as(decodeSchema(schema.right)).map(Fallback.Right(_))
              case Right(first) =>
                if (!schema.fullDecode) Right(Fallback.Left(first))
                else
                  right.as(decodeSchema(schema.right)) match {
                    case Left(_)       => Right(Fallback.Left(first))
                    case Right(second) => Right(Fallback.Both(first, second))
                  }
            }
          case _                    => Left(DecodingFailure("Too many items on fallback", c.history))
        }
      } else {
        decodeSchema(schema.left)(c).map(Fallback.Left(_)).left.flatMap { _ =>
          decodeSchema(schema.right)(c).map(Fallback.Right(_)).left.flatMap { _ =>
            decodeFail("Fallback decoder was unable to decode both left and right sides")(c)
          }
        }
      }
    }
  }

  private def encodeRecord[Z](
    structure: Seq[Schema.Field[Z, _]],
    config: CirceCodec.Config,
  ): Encoder[ListMap[String, _]] = new Encoder[ListMap[String, _]] {
    override def apply(value: ListMap[String, _]) = {
      if (structure.isEmpty) Json.obj()
      else {
        Json.obj(
          structure.collect {
            case f @ Schema.Field(_, a, _, _, _, _)
                if !f.transient && !isEmptyOptionalValue(f, value(f.fieldName), config) =>
              KeyEncoder.encodeKeyString(f.fieldName) ->
                encodeSchema(a.asInstanceOf[Schema[Any]], config)(value(f.fieldName))
          }: _*,
        )
      }
    }
  }

  private def isEmptyOptionalValue(schema: Schema.Field[_, _], value: Any, config: CirceCodec.Config) = {
    val ignoreEmptyCollections = config.ignoreEmptyCollections || schema.optional
    val isEmptyCollection      = value match {
      case _: Iterable[_] => value.asInstanceOf[Iterable[_]].isEmpty
      case None           => true
      case _              => false
    }

    ignoreEmptyCollections && isEmptyCollection
  }

  def decodeRecord[Z](
    structure: Seq[Schema.Field[Z, _]],
    rejectAdditionalFields: Boolean,
  ): Decoder[ListMap[String, Any]] = new Decoder[ListMap[String, Any]] {
    override def apply(c: HCursor) = {
      c.as[JsonObject].flatMap { obj =>
        try {
          val builder: ChunkBuilder[(String, Any)] = ChunkBuilder.make[(String, Any)](structure.size)
          obj.toIterable.foreach { case (key, value) =>
            structure.find(_.nameAndAliases.contains(key)) match {
              case Some(s @ Schema.Field(_, schema, _, _, _, _)) =>
                val fieldName = s.fieldName
                decodeSchema(schema)(value.hcursor) match {
                  case Left(error)  => throw error
                  case Right(value) =>
                    builder +=
                      KeyDecoder
                        .decodeKeyString(fieldName)
                        .getOrElse(throw DecodingFailure(s"Failed to decode field: $fieldName", c.history)) -> value
                }
              case None if rejectAdditionalFields                =>
                throw DecodingFailure(s"Unexpected field: $key", c.history)
              case None                                          => ()
            }
          }

          val tuples                       = builder.result()
          val collectedFields: Set[String] = tuples.map { case (fieldName, _) => fieldName }.toSet
          val resultBuilder                = ListMap.newBuilder[String, Any]

          // add fields with default values if they are not present in the JSON
          structure.foreach { field =>
            if (!collectedFields.contains(field.fieldName) && field.optional && field.defaultValue.isDefined) {
              val value = field.fieldName -> field.defaultValue.get
              resultBuilder += value
            }
          }
          Right((resultBuilder ++= tuples).result())
        } catch {
          case df: DecodingFailure => Left(df)
          case e: Exception        => Left(DecodingFailure(e.getMessage(), c.history))
        }
      }
    }
  }

  // scalafmt: { maxColumn = 400, optIn.configStyleArguments = false }
  def encodeCaseClass[A](schema: Schema.Record[A], config: CirceCodec.Config, discriminatorTuple: DiscriminatorTuple): Encoder[A] = new Encoder[A] {
    override def apply(a: A) = {
      Json.obj(
        (discriminatorTuple.map { discriminator =>
          val (tag, caseTpeName) = discriminator
          KeyEncoder.encodeKeyString(tag.tag) -> Encoder.encodeString(KeyEncoder.encodeKeyString(caseTpeName))
        } ++
          schema.nonTransientFields.map { case s: Schema.Field[A, _] =>
            val valueEncoder =
              try {
                encodeSchema(s.schema, config)
              } catch {
                case e: Throwable => throw new RuntimeException(s"Failed to encode field '${s.name}' in $schema'", e)
              }
            val value        = s.get(a)
            val valueJson    = valueEncoder(value)

            if (!isEmptyOptionalValue(s, value, config) && (!valueJson.isNull || !config.ignoreNullValues))
              Some(KeyEncoder.encodeKeyString(s.name) -> valueJson)
            else None
          }.flatten).toSeq: _*,
      )
    }
  }

  def decodeCaseClass0[Z](schema: Schema.CaseClass0[Z]): Decoder[Z] = new Decoder[Z] {
    override def apply(c: HCursor) = {
      val rejectExtraFields = schema.annotations.collectFirst { case _: rejectExtraFields => () }.isDefined

      if (rejectExtraFields) Left(DecodingFailure("Extra fields found", c.history))
      else Right(schema.defaultConstruct())
    }
  }

  def decodeCaseClass1[A, Z](schema: Schema.CaseClass1[A, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.defaultConstruct(buffer(0).asInstanceOf[A])
    }
  }

  def decodeCaseClass2[A1, A2, Z](schema: Schema.CaseClass2[A1, A2, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2])
    }
  }

  def decodeCaseClass3[A1, A2, A3, Z](schema: Schema.CaseClass3[A1, A2, A3, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3])
    }
  }

  def decodeCaseClass4[A1, A2, A3, A4, Z](schema: Schema.CaseClass4[A1, A2, A3, A4, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4])
    }
  }

  def decodeCaseClass5[A1, A2, A3, A4, A5, Z](schema: Schema.CaseClass5[A1, A2, A3, A4, A5, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5])
    }
  }

  def decodeCaseClass6[A1, A2, A3, A4, A5, A6, Z](schema: Schema.CaseClass6[A1, A2, A3, A4, A5, A6, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6])
    }
  }

  def decodeCaseClass7[A1, A2, A3, A4, A5, A6, A7, Z](schema: Schema.CaseClass7[A1, A2, A3, A4, A5, A6, A7, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7])
    }
  }

  def decodeCaseClass8[A1, A2, A3, A4, A5, A6, A7, A8, Z](schema: Schema.CaseClass8[A1, A2, A3, A4, A5, A6, A7, A8, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8])
    }
  }

  def decodeCaseClass9[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z](schema: Schema.CaseClass9[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9])
    }
  }

  def decodeCaseClass10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z](schema: Schema.CaseClass10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10])
    }
  }

  def decodeCaseClass11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z](schema: Schema.CaseClass11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10], buffer(10).asInstanceOf[A11])
    }
  }

  def decodeCaseClass12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z](schema: Schema.CaseClass12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10], buffer(10).asInstanceOf[A11], buffer(11).asInstanceOf[A12])
    }
  }

  def decodeCaseClass13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z](schema: Schema.CaseClass13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10], buffer(10).asInstanceOf[A11], buffer(11).asInstanceOf[A12], buffer(12).asInstanceOf[A13])
    }
  }

  def decodeCaseClass14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z](schema: Schema.CaseClass14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
      )
    }
  }

  def decodeCaseClass15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z](schema: Schema.CaseClass15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
      )
    }
  }

  def decodeCaseClass16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z](schema: Schema.CaseClass16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
      )
    }
  }

  def decodeCaseClass17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, Z](schema: Schema.CaseClass17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
        buffer(16).asInstanceOf[A17],
      )
    }
  }

  def decodeCaseClass18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, Z](schema: Schema.CaseClass18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
        buffer(16).asInstanceOf[A17],
        buffer(17).asInstanceOf[A18],
      )
    }
  }

  def decodeCaseClass19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, Z](schema: Schema.CaseClass19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
        buffer(16).asInstanceOf[A17],
        buffer(17).asInstanceOf[A18],
        buffer(18).asInstanceOf[A19],
      )
    }
  }

  def decodeCaseClass20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, Z](schema: Schema.CaseClass20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
        buffer(16).asInstanceOf[A17],
        buffer(17).asInstanceOf[A18],
        buffer(18).asInstanceOf[A19],
        buffer(19).asInstanceOf[A20],
      )
    }
  }

  def decodeCaseClass21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, Z](schema: Schema.CaseClass21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
        buffer(16).asInstanceOf[A17],
        buffer(17).asInstanceOf[A18],
        buffer(18).asInstanceOf[A19],
        buffer(19).asInstanceOf[A20],
        buffer(20).asInstanceOf[A21],
      )
    }
  }

  def decodeCaseClass22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, Z](schema: Schema.CaseClass22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, Z], discriminator: Int): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(
        buffer(0).asInstanceOf[A1],
        buffer(1).asInstanceOf[A2],
        buffer(2).asInstanceOf[A3],
        buffer(3).asInstanceOf[A4],
        buffer(4).asInstanceOf[A5],
        buffer(5).asInstanceOf[A6],
        buffer(6).asInstanceOf[A7],
        buffer(7).asInstanceOf[A8],
        buffer(8).asInstanceOf[A9],
        buffer(9).asInstanceOf[A10],
        buffer(10).asInstanceOf[A11],
        buffer(11).asInstanceOf[A12],
        buffer(12).asInstanceOf[A13],
        buffer(13).asInstanceOf[A14],
        buffer(14).asInstanceOf[A15],
        buffer(15).asInstanceOf[A16],
        buffer(16).asInstanceOf[A17],
        buffer(17).asInstanceOf[A18],
        buffer(18).asInstanceOf[A19],
        buffer(19).asInstanceOf[A20],
        buffer(20).asInstanceOf[A21],
        buffer(21).asInstanceOf[A22],
      )
    }
  }

  private def decodeFields[Z](caseClassSchema: Schema.Record[Z], discriminator: Int): Decoder[Array[Any]] = new Decoder[Array[Any]] {
    override def apply(c: HCursor) = {
      val fields            = caseClassSchema.fields
      val buffer            = Array.ofDim[Any](fields.length)
      val fieldNames        = fields.map(_.name.asInstanceOf[String]).toArray
      val schemas           = fields.map(_.schema).toArray
      val fieldAliases      = fields.flatMap { case Schema.Field(name, _, annotations, _, _, _) =>
        val aliases = annotations.collectFirst { case a: fieldNameAliases => a.aliases }.getOrElse(Nil)
        aliases.map(_ -> fieldNames.indexOf(name)) :+ (name -> fieldNames.indexOf(name))
      }.toMap
      val rejectExtraFields = caseClassSchema.annotations.collectFirst { case _: rejectExtraFields => () }.isDefined

      c.keys match {
        case None       => Left(DecodingFailure("No fields found", c.history))
        case Some(keys) =>
          try {
            var i = 0
            keys.foreach { key =>
              fieldAliases.getOrElse(key, -1) match {
                case -1  =>
                  if (i != discriminator && rejectExtraFields)
                    throw DecodingFailure(s"Extra field: $key", c.history)
                case idx =>
                  if (buffer(idx) != null) throw DecodingFailure(s"Duplicate field: $key", c.history)
                  else
                    c.get(key)(decodeSchema(schemas(idx))) match {
                      case Left(value)  => throw DecodingFailure(s"Failed to decode field: $key", c.history)
                      case Right(value) => buffer(idx) = value
                    }
              }
              i += 1
            }

            i = 0
            while (i < fields.length) {
              if (buffer(i) == null) {
                if ((fields(i).optional || fields(i).transient) && fields(i).defaultValue.isDefined) {
                  buffer(i) = fields(i).defaultValue.get
                } else {
                  val schema = fields(i).schema match {
                    case l @ Schema.Lazy(_) => l.schema
                    case _                  => schemas(i)
                  }

                  schema match {
                    case collection: Schema.Collection[_, _] => buffer(i) = collection.empty
                    case _: Schema.Optional[_]               => buffer(i) = None
                    case _                                   =>
                      throw DecodingFailure(s"Failed to decode field: ${fields(i).name}", c.history)
                  }
                }
              }
              i += 1
            }

            Right(buffer)
          } catch {
            case e: DecodingFailure => Left(e)
          }
      }
    }
  }
}
