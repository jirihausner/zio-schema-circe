package zio.schema.codec.circe.jsoniter.internal

import com.github.plokhotnyuk.jsoniter_scala.circe.CirceCodecs
import io.circe.{Decoder, Encoder}
import zio.schema.StandardType

private[jsoniter] object Codecs extends zio.schema.codec.circe.internal.Codecs {

  override def encodePrimitive[A](standardType: StandardType[A]): Encoder[A] = standardType match {
    case StandardType.UnitType           => Encoder.encodeUnit
    case StandardType.StringType         => Encoder.encodeString
    case StandardType.BoolType           => Encoder.encodeBoolean
    case StandardType.ByteType           => CirceCodecs.byteC3C
    case StandardType.ShortType          => CirceCodecs.shortC3C
    case StandardType.IntType            => CirceCodecs.intC3C
    case StandardType.LongType           => CirceCodecs.longC3C
    case StandardType.FloatType          => CirceCodecs.floatC3C
    case StandardType.DoubleType         => CirceCodecs.doubleC3C
    case StandardType.BinaryType         => encodeChunk(CirceCodecs.byteC3C)
    case StandardType.CharType           => Encoder.encodeChar
    case StandardType.BigIntegerType     => CirceCodecs.bigIntC3C.contramap[java.math.BigInteger](new BigInt(_))
    case StandardType.BigDecimalType     => CirceCodecs.bigDecimalC3C.contramap[java.math.BigDecimal](new BigDecimal(_))
    case StandardType.UUIDType           => Encoder.encodeUUID
    case StandardType.DayOfWeekType      => Encoder.encodeString.contramap[java.time.DayOfWeek](_.toString)
    case StandardType.DurationType       => CirceCodecs.durationC3C
    case StandardType.InstantType        => CirceCodecs.instantC3C
    case StandardType.LocalDateType      => CirceCodecs.localDateC3C
    case StandardType.LocalDateTimeType  => CirceCodecs.localDateTimeC3C
    case StandardType.LocalTimeType      => CirceCodecs.localTimeC3C
    case StandardType.MonthType          => Encoder.encodeString.contramap[java.time.Month](_.toString)
    case StandardType.MonthDayType       => CirceCodecs.monthDayC3C
    case StandardType.OffsetDateTimeType => CirceCodecs.offsetDateTimeC3C
    case StandardType.OffsetTimeType     => CirceCodecs.offsetTimeC3C
    case StandardType.PeriodType         => CirceCodecs.periodC3C
    case StandardType.YearType           => CirceCodecs.yearC3C
    case StandardType.YearMonthType      => CirceCodecs.yearMonthC3C
    case StandardType.ZonedDateTimeType  => CirceCodecs.zonedDateTimeC3C
    case StandardType.ZoneIdType         => Encoder.encodeZoneId
    case StandardType.ZoneOffsetType     => Encoder.encodeZoneOffset
    case StandardType.CurrencyType       => Encoder.encodeString.contramap[java.util.Currency](_.toString)
  }

  override def decodePrimitive[A](standardType: StandardType[A]): Decoder[A] = standardType match {
    case StandardType.UnitType           => Decoder.decodeUnit
    case StandardType.StringType         => Decoder.decodeString
    case StandardType.BoolType           => Decoder.decodeBoolean
    case StandardType.ByteType           => CirceCodecs.byteC3C
    case StandardType.ShortType          => CirceCodecs.shortC3C
    case StandardType.IntType            => CirceCodecs.intC3C
    case StandardType.LongType           => CirceCodecs.longC3C
    case StandardType.FloatType          => CirceCodecs.floatC3C
    case StandardType.DoubleType         => CirceCodecs.doubleC3C
    case StandardType.BinaryType         => decodeChunk(CirceCodecs.byteC3C)
    case StandardType.CharType           => Decoder.decodeChar
    case StandardType.BigIntegerType     => CirceCodecs.bigIntC3C.map(_.underlying)
    case StandardType.BigDecimalType     => CirceCodecs.bigDecimalC3C.map(_.underlying)
    case StandardType.UUIDType           => decodeUUID
    case StandardType.DayOfWeekType      => Decoder.decodeString.emap(parseJavaTime(java.time.DayOfWeek.valueOf, _))
    case StandardType.DurationType       => CirceCodecs.durationC3C
    case StandardType.InstantType        => CirceCodecs.instantC3C
    case StandardType.LocalDateType      => CirceCodecs.localDateC3C
    case StandardType.LocalDateTimeType  => CirceCodecs.localDateTimeC3C
    case StandardType.LocalTimeType      => CirceCodecs.localTimeC3C
    case StandardType.MonthType          => Decoder.decodeString.emap(parseJavaTime(java.time.Month.valueOf, _))
    case StandardType.MonthDayType       => CirceCodecs.monthDayC3C
    case StandardType.OffsetDateTimeType => CirceCodecs.offsetDateTimeC3C
    case StandardType.OffsetTimeType     => CirceCodecs.offsetTimeC3C
    case StandardType.PeriodType         => CirceCodecs.periodC3C
    case StandardType.YearType           => CirceCodecs.yearC3C
    case StandardType.YearMonthType      => CirceCodecs.yearMonthC3C
    case StandardType.ZonedDateTimeType  => CirceCodecs.zonedDateTimeC3C
    case StandardType.ZoneIdType         => Decoder.decodeZoneId
    case StandardType.ZoneOffsetType     => Decoder.decodeZoneOffset
    case StandardType.CurrencyType       => decodeCurrency
  }
}
