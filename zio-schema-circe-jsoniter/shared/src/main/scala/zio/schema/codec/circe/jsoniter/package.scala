package zio.schema.codec.circe

import com.github.plokhotnyuk.jsoniter_scala.circe.CirceCodecs
import io.circe.{Json, JsonNumber, JsonObject}
import zio.Chunk
import zio.schema.annotation.directDynamicMapping
import zio.schema.codec.circe.internal._
import zio.schema.{DynamicValue, Schema, StandardType}

import java.util.Base64

package object jsoniter {

  implicit val schemaJson: Schema[Json] =
    Schema.dynamicValue
      .transform(toJson, dynamicValueFromJson)
      .annotate(directDynamicMapping())

  implicit val schemaJsonObject: Schema[JsonObject] =
    Schema.dynamicValue
      .transform(dynamicValueToJsonObject, (obj: JsonObject) => dynamicValueFromJson(obj.toJson))
      .annotate(directDynamicMapping())

  implicit val schemaJsonNumber: Schema[JsonNumber] =
    Schema.dynamicValue
      .transformOrFail(
        toJsonNumber,
        (number: JsonNumber) => Right(dynamicValueFromJson(Json.fromJsonNumber(number))),
      )
      .annotate(directDynamicMapping())

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
    case other                                       => dynamicValueToJson(other)
  }

  private[circe] def toJsonNumber(dv: DynamicValue): Either[String, JsonNumber] = {
    dv match {
      case DynamicValue.Primitive(value, standardType) =>
        val json = standardType.asInstanceOf[StandardType[_]] match {
          case StandardType.UnitType           => JsonObject.empty.toJson
          case StandardType.StringType         => Json.fromString(value.asInstanceOf[String])
          case StandardType.BoolType           => Json.fromBoolean(value.asInstanceOf[Boolean])
          case StandardType.ByteType           => CirceCodecs.byteC3C(value.asInstanceOf[Byte])
          case StandardType.ShortType          => CirceCodecs.shortC3C(value.asInstanceOf[Short])
          case StandardType.IntType            => CirceCodecs.intC3C(value.asInstanceOf[Int])
          case StandardType.LongType           => CirceCodecs.longC3C(value.asInstanceOf[Long])
          case StandardType.FloatType          => CirceCodecs.floatC3C(value.asInstanceOf[Float])
          case StandardType.DoubleType         => CirceCodecs.doubleC3C(value.asInstanceOf[Double])
          case StandardType.BinaryType         =>
            Json.fromString(Base64.getEncoder.encodeToString(value.asInstanceOf[Chunk[Byte]].toArray))
          case StandardType.CharType           => Json.fromString(value.asInstanceOf[Char].toString)
          case StandardType.UUIDType           => Json.fromString(value.asInstanceOf[java.util.UUID].toString)
          case StandardType.BigDecimalType     => CirceCodecs.bigDecimalC3C(value.asInstanceOf[java.math.BigDecimal])
          case StandardType.BigIntegerType     => CirceCodecs.bigIntC3C(value.asInstanceOf[java.math.BigInteger])
          case StandardType.DayOfWeekType      => Json.fromString(value.asInstanceOf[java.time.DayOfWeek].toString)
          case StandardType.MonthType          => Json.fromString(value.asInstanceOf[java.time.Month].toString)
          case StandardType.MonthDayType       => CirceCodecs.monthDayC3C(value.asInstanceOf[java.time.MonthDay])
          case StandardType.PeriodType         => CirceCodecs.periodC3C(value.asInstanceOf[java.time.Period])
          case StandardType.YearType           => CirceCodecs.yearC3C(value.asInstanceOf[java.time.Year])
          case StandardType.YearMonthType      => CirceCodecs.yearMonthC3C(value.asInstanceOf[java.time.YearMonth])
          case StandardType.ZoneIdType         => Json.fromString(value.asInstanceOf[java.time.ZoneId].toString)
          case StandardType.ZoneOffsetType     => Json.fromString(value.asInstanceOf[java.time.ZoneOffset].toString)
          case StandardType.DurationType       => CirceCodecs.durationC3C(value.asInstanceOf[java.time.Duration])
          case StandardType.InstantType        => CirceCodecs.instantC3C(value.asInstanceOf[java.time.Instant])
          case StandardType.LocalDateType      => CirceCodecs.localDateC3C(value.asInstanceOf[java.time.LocalDate])
          case StandardType.LocalTimeType      => CirceCodecs.localTimeC3C(value.asInstanceOf[java.time.LocalTime])
          case StandardType.LocalDateTimeType  =>
            CirceCodecs.localDateTimeC3C(value.asInstanceOf[java.time.LocalDateTime])
          case StandardType.OffsetTimeType     => CirceCodecs.offsetTimeC3C(value.asInstanceOf[java.time.OffsetTime])
          case StandardType.OffsetDateTimeType =>
            CirceCodecs.offsetDateTimeC3C(value.asInstanceOf[java.time.OffsetDateTime])
          case StandardType.ZonedDateTimeType  =>
            CirceCodecs.zonedDateTimeC3C(value.asInstanceOf[java.time.ZonedDateTime])
          case StandardType.CurrencyType       => Json.fromString(value.asInstanceOf[java.util.Currency].toString)
        }
        json.asNumber.toRight(s"Failed to parse ${json.noSpaces} as number")
      case _                                           =>
        Left(s"DynamicValue.${dv.getClass.getName} is not supported")
    }
  }
}
