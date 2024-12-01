package zio.schema.codec.circe

import com.github.plokhotnyuk.jsoniter_scala.circe.CirceCodecs
import io.circe.{Json, JsonNumber, JsonObject}
import zio.Chunk
import zio.schema.annotation.directDynamicMapping
import zio.schema.codec.circe
import zio.schema.{DynamicValue, Schema, StandardType}

import java.util.Base64

package object jsoniter {

  implicit val schemaJson: Schema[Json] = circe.schemaJson
  Schema.dynamicValue.transform(toJson, circe.fromJson).annotate(directDynamicMapping())

  implicit val schemaJsonObject: Schema[JsonObject] = circe.schemaJsonObject

  implicit val schemaJsonNumber: Schema[JsonNumber] = circe.schemaJsonNumber

  private def toJson(dv: DynamicValue): Json = dv match {
    case DynamicValue.Primitive(value, standardType) =>
      standardType.asInstanceOf[StandardType[_]] match {
        case StandardType.UnitType          => JsonObject.empty.toJson
        case StandardType.StringType        => Json.fromString(value.asInstanceOf[String])
        case StandardType.BoolType          => Json.fromBoolean(value.asInstanceOf[Boolean])
        case StandardType.ByteType          => CirceCodecs.byteC3C(value.asInstanceOf[Byte])
        case StandardType.ShortType         => CirceCodecs.shortC3C(value.asInstanceOf[Short])
        case StandardType.IntType           => CirceCodecs.intC3C(value.asInstanceOf[Int])
        case StandardType.LongType          => CirceCodecs.longC3C(value.asInstanceOf[Long])
        case StandardType.FloatType         => CirceCodecs.floatC3C(value.asInstanceOf[Float])
        case StandardType.DoubleType        => CirceCodecs.doubleC3C(value.asInstanceOf[Double])
        case StandardType.BinaryType        =>
          Json.fromString(Base64.getEncoder.encodeToString(value.asInstanceOf[Chunk[Byte]].toArray))
        case StandardType.CharType          => Json.fromString(value.asInstanceOf[Char].toString)
        case StandardType.UUIDType          => Json.fromString(value.asInstanceOf[java.util.UUID].toString)
        case StandardType.BigDecimalType    => CirceCodecs.bigDecimalC3C(value.asInstanceOf[java.math.BigDecimal])
        case StandardType.BigIntegerType    => CirceCodecs.bigIntC3C(value.asInstanceOf[java.math.BigInteger])
        case StandardType.DayOfWeekType     => Json.fromString(value.asInstanceOf[java.time.DayOfWeek].toString)
        case StandardType.MonthType         => Json.fromString(value.asInstanceOf[java.time.Month].toString)
        case StandardType.MonthDayType      => CirceCodecs.monthDayC3C(value.asInstanceOf[java.time.MonthDay])
        case StandardType.PeriodType        => CirceCodecs.periodC3C(value.asInstanceOf[java.time.Period])
        case StandardType.YearType          => CirceCodecs.yearC3C(value.asInstanceOf[java.time.Year])
        case StandardType.YearMonthType     => CirceCodecs.yearMonthC3C(value.asInstanceOf[java.time.YearMonth])
        case StandardType.ZoneIdType        => Json.fromString(value.asInstanceOf[java.time.ZoneId].toString)
        case StandardType.ZoneOffsetType    => Json.fromString(value.asInstanceOf[java.time.ZoneOffset].toString)
        case StandardType.DurationType      => CirceCodecs.durationC3C(value.asInstanceOf[java.time.Duration])
        case StandardType.InstantType       => CirceCodecs.instantC3C(value.asInstanceOf[java.time.Instant])
        case StandardType.LocalDateType     => CirceCodecs.localDateC3C(value.asInstanceOf[java.time.LocalDate])
        case StandardType.LocalTimeType     => CirceCodecs.localTimeC3C(value.asInstanceOf[java.time.LocalTime])
        case StandardType.LocalDateTimeType => CirceCodecs.localDateTimeC3C(value.asInstanceOf[java.time.LocalDateTime])
        case StandardType.OffsetTimeType    => CirceCodecs.offsetTimeC3C(value.asInstanceOf[java.time.OffsetTime])
        case StandardType.OffsetDateTimeType =>
          CirceCodecs.offsetDateTimeC3C(value.asInstanceOf[java.time.OffsetDateTime])
        case StandardType.ZonedDateTimeType => CirceCodecs.zonedDateTimeC3C(value.asInstanceOf[java.time.ZonedDateTime])
        case StandardType.CurrencyType      => Json.fromString(value.asInstanceOf[java.util.Currency].toString)
      }
    case other                                       => circe.toJson(other)
  }
}
