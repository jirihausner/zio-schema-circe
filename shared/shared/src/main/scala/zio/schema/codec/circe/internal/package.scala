package zio.schema.codec.circe

import io.circe.{Json, JsonNumber, JsonObject}
import zio.Chunk
import zio.schema.{DynamicValue, StandardType, TypeId}

import java.util.Base64
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap

package object internal {

  @inline
  private[circe] def charSequenceToByteChunk(chars: CharSequence): Chunk[Byte] = {
    val bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
    Chunk.fromByteBuffer(bytes)
  }

  @inline
  private[circe] def byteChunkToString(bytes: Chunk[Byte]): String = {
    new String(bytes.toArray, StandardCharsets.UTF_8)
  }

  private[circe] def dynamicValueToJson(dv: DynamicValue): Json = dv match {
    case DynamicValue.Record(_, values)              =>
      JsonObject.fromMap(values.map { case (k, v) => (k, dynamicValueToJson(v)) }).toJson
    case DynamicValue.Enumeration(_, _)              =>
      throw new Exception("DynamicValue.Enumeration is not supported")
    case DynamicValue.Sequence(values)               =>
      Json.arr(values.map(dynamicValueToJson): _*)
    case DynamicValue.Dictionary(_)                  =>
      throw new Exception("DynamicValue.Dictionary is not supported")
    case DynamicValue.SetValue(values)               =>
      Json.arr(values.map(dynamicValueToJson).toSeq: _*)
    case DynamicValue.Primitive(value, standardType) =>
      standardType.asInstanceOf[StandardType[_]] match {
        case StandardType.UnitType           => JsonObject.empty.toJson
        case StandardType.StringType         => Json.fromString(value.asInstanceOf[String])
        case StandardType.BoolType           => Json.fromBoolean(value.asInstanceOf[Boolean])
        case StandardType.ByteType           => Json.fromInt(value.asInstanceOf[Byte].toInt)
        case StandardType.ShortType          => Json.fromInt(value.asInstanceOf[Short].toInt)
        case StandardType.IntType            => Json.fromInt(value.asInstanceOf[Int])
        case StandardType.LongType           => Json.fromLong(value.asInstanceOf[Long])
        case StandardType.FloatType          => Json.fromFloatOrNull(value.asInstanceOf[Float])
        case StandardType.DoubleType         => Json.fromDoubleOrNull(value.asInstanceOf[Double])
        case StandardType.BinaryType         =>
          Json.fromString(Base64.getEncoder.encodeToString(value.asInstanceOf[Chunk[Byte]].toArray))
        case StandardType.CharType           => Json.fromString(value.asInstanceOf[Char].toString)
        case StandardType.UUIDType           => Json.fromString(value.asInstanceOf[java.util.UUID].toString)
        case StandardType.BigDecimalType     => Json.fromBigDecimal(value.asInstanceOf[java.math.BigDecimal])
        case StandardType.BigIntegerType     => Json.fromBigInt(value.asInstanceOf[java.math.BigInteger])
        case StandardType.DayOfWeekType      => Json.fromString(value.asInstanceOf[java.time.DayOfWeek].toString)
        case StandardType.MonthType          => Json.fromString(value.asInstanceOf[java.time.Month].toString)
        case StandardType.MonthDayType       => Json.fromString(value.asInstanceOf[java.time.MonthDay].toString)
        case StandardType.PeriodType         => Json.fromString(value.asInstanceOf[java.time.Period].toString)
        case StandardType.YearType           => Json.fromInt(value.asInstanceOf[java.time.Year].getValue)
        case StandardType.YearMonthType      => Json.fromString(value.asInstanceOf[java.time.YearMonth].toString)
        case StandardType.ZoneIdType         => Json.fromString(value.asInstanceOf[java.time.ZoneId].toString)
        case StandardType.ZoneOffsetType     => Json.fromString(value.asInstanceOf[java.time.ZoneOffset].toString)
        case StandardType.DurationType       => Json.fromString(value.asInstanceOf[java.time.Duration].toString)
        case StandardType.InstantType        => Json.fromString(value.asInstanceOf[java.time.Instant].toString)
        case StandardType.LocalDateType      => Json.fromString(value.asInstanceOf[java.time.LocalDate].toString)
        case StandardType.LocalTimeType      => Json.fromString(value.asInstanceOf[java.time.LocalTime].toString)
        case StandardType.LocalDateTimeType  => Json.fromString(value.asInstanceOf[java.time.LocalDateTime].toString)
        case StandardType.OffsetTimeType     => Json.fromString(value.asInstanceOf[java.time.OffsetTime].toString)
        case StandardType.OffsetDateTimeType => Json.fromString(value.asInstanceOf[java.time.OffsetDateTime].toString)
        case StandardType.ZonedDateTimeType  => Json.fromString(value.asInstanceOf[java.time.ZonedDateTime].toString)
        case StandardType.CurrencyType       => Json.fromString(value.asInstanceOf[java.util.Currency].toString)
      }
    case DynamicValue.Singleton(_)                   => Json.fromJsonObject(JsonObject.empty)
    case DynamicValue.SomeValue(value)               => dynamicValueToJson(value)
    case DynamicValue.NoneValue                      => Json.Null
    case DynamicValue.Tuple(left, right)             => Json.arr(dynamicValueToJson(left), dynamicValueToJson(right))
    case DynamicValue.LeftValue(value)               => Json.obj("Left" -> dynamicValueToJson(value))
    case DynamicValue.RightValue(value)              => Json.obj("Right" -> dynamicValueToJson(value))
    case DynamicValue.BothValue(_, _)                => throw new Exception("DynamicValue.BothValue is not supported")
    case DynamicValue.DynamicAst(_)                  => throw new Exception("DynamicValue.DynamicAst is not supported")
    case DynamicValue.Error(_)                       => throw new Exception("DynamicValue.Error is not supported")
  }

  private[circe] def dynamicValueToJsonObject(dv: DynamicValue): JsonObject =
    dv match {
      case DynamicValue.Record(_, values) =>
        JsonObject.fromMap(values.map { case (k, v) => (k, dynamicValueToJson(v)) })
      case DynamicValue.Enumeration(_, _) => throw new Exception("DynamicValue.Enumeration is not supported")
      case DynamicValue.Sequence(values)  => throw new Exception("DynamicValue.Sequence is not supported")
      case DynamicValue.Dictionary(_)     => throw new Exception("DynamicValue.Dictionary is not supported")
      case DynamicValue.SetValue(values)  => throw new Exception("DynamicValue.SetValue is not supported")
      case DynamicValue.Primitive(value, standardType) =>
        standardType.asInstanceOf[StandardType[_]] match {
          case StandardType.UnitType => JsonObject.empty
          case tpe                   => throw new Exception(s"StandardType.${tpe.getClass.getName} is not supported")
        }
      case DynamicValue.Singleton(_)                   => JsonObject.empty
      case DynamicValue.SomeValue(value)               => dynamicValueToJsonObject(value)
      case DynamicValue.NoneValue                      => JsonObject.empty
      case DynamicValue.Tuple(left, right)             => throw new Exception("DynamicValue.Tuple is not supported")
      case DynamicValue.LeftValue(value)               => JsonObject("Left" -> dynamicValueToJson(value))
      case DynamicValue.RightValue(value)              => JsonObject("Right" -> dynamicValueToJson(value))
      case DynamicValue.BothValue(_, _)                => throw new Exception("DynamicValue.BothValue is not supported")
      case DynamicValue.DynamicAst(_) => throw new Exception("DynamicValue.DynamicAst is not supported")
      case DynamicValue.Error(_)      => throw new Exception("DynamicValue.Error is not supported")
    }

  private[circe] def dynamicValueToJsonNumber(dv: DynamicValue): Either[String, JsonNumber] = {
    dv match {
      case DynamicValue.Primitive(value, standardType) =>
        val json = standardType.asInstanceOf[StandardType[_]] match {
          case StandardType.UnitType           => JsonObject.empty.toJson
          case StandardType.StringType         => Json.fromString(value.asInstanceOf[String])
          case StandardType.BoolType           => Json.fromBoolean(value.asInstanceOf[Boolean])
          case StandardType.ByteType           => Json.fromInt(value.asInstanceOf[Byte].toInt)
          case StandardType.ShortType          => Json.fromInt(value.asInstanceOf[Short].toInt)
          case StandardType.IntType            => Json.fromInt(value.asInstanceOf[Int])
          case StandardType.LongType           => Json.fromLong(value.asInstanceOf[Long])
          case StandardType.FloatType          => Json.fromFloatOrNull(value.asInstanceOf[Float])
          case StandardType.DoubleType         => Json.fromDoubleOrNull(value.asInstanceOf[Double])
          case StandardType.BinaryType         =>
            Json.fromString(Base64.getEncoder.encodeToString(value.asInstanceOf[Chunk[Byte]].toArray))
          case StandardType.CharType           => Json.fromString(value.asInstanceOf[Char].toString)
          case StandardType.UUIDType           => Json.fromString(value.asInstanceOf[java.util.UUID].toString)
          case StandardType.BigDecimalType     => Json.fromBigDecimal(value.asInstanceOf[java.math.BigDecimal])
          case StandardType.BigIntegerType     => Json.fromBigInt(value.asInstanceOf[java.math.BigInteger])
          case StandardType.DayOfWeekType      => Json.fromString(value.asInstanceOf[java.time.DayOfWeek].toString)
          case StandardType.MonthType          => Json.fromString(value.asInstanceOf[java.time.Month].toString)
          case StandardType.MonthDayType       => Json.fromString(value.asInstanceOf[java.time.MonthDay].toString)
          case StandardType.PeriodType         => Json.fromString(value.asInstanceOf[java.time.Period].toString)
          case StandardType.YearType           => Json.fromInt(value.asInstanceOf[java.time.Year].getValue)
          case StandardType.YearMonthType      => Json.fromString(value.asInstanceOf[java.time.YearMonth].toString)
          case StandardType.ZoneIdType         => Json.fromString(value.asInstanceOf[java.time.ZoneId].toString)
          case StandardType.ZoneOffsetType     => Json.fromString(value.asInstanceOf[java.time.ZoneOffset].toString)
          case StandardType.DurationType       => Json.fromString(value.asInstanceOf[java.time.Duration].toString)
          case StandardType.InstantType        => Json.fromString(value.asInstanceOf[java.time.Instant].toString)
          case StandardType.LocalDateType      => Json.fromString(value.asInstanceOf[java.time.LocalDate].toString)
          case StandardType.LocalTimeType      => Json.fromString(value.asInstanceOf[java.time.LocalTime].toString)
          case StandardType.LocalDateTimeType  => Json.fromString(value.asInstanceOf[java.time.LocalDateTime].toString)
          case StandardType.OffsetTimeType     => Json.fromString(value.asInstanceOf[java.time.OffsetTime].toString)
          case StandardType.OffsetDateTimeType => Json.fromString(value.asInstanceOf[java.time.OffsetDateTime].toString)
          case StandardType.ZonedDateTimeType  => Json.fromString(value.asInstanceOf[java.time.ZonedDateTime].toString)
          case StandardType.CurrencyType       => Json.fromString(value.asInstanceOf[java.util.Currency].toString)
        }
        json.asNumber.toRight(s"Failed to parse ${json.noSpaces} as number")
      case _                                           =>
        Left(s"DynamicValue.${dv.getClass.getName} is not supported")
    }
  }

  private val dynamicValueFolder: Json.Folder[DynamicValue] = new Json.Folder[DynamicValue] {
    val onNull: DynamicValue                        = DynamicValue.NoneValue
    def onBoolean(value: Boolean): DynamicValue     = DynamicValue.Primitive(value, StandardType.BoolType)
    def onNumber(value: JsonNumber): DynamicValue   =
      value.toLong.map(DynamicValue.Primitive(_, StandardType.LongType)).getOrElse {
        value.toBigInt.map(bi => DynamicValue.Primitive(bi.underlying, StandardType.BigIntegerType)).getOrElse {
          value.toBigDecimal.map(bd => DynamicValue.Primitive(bd.underlying, StandardType.BigDecimalType)).getOrElse {
            DynamicValue.Primitive(value.toDouble, StandardType.DoubleType)
          }
        }
      }
    def onString(value: String): DynamicValue       = DynamicValue.Primitive(value, StandardType.StringType)
    def onArray(values: Vector[Json]): DynamicValue =
      DynamicValue.Sequence(Chunk.fromIterable(values.map(dynamicValueFromJson)))
    def onObject(obj: JsonObject): DynamicValue     =
      DynamicValue.Record(
        TypeId.Structural,
        ListMap(obj.toMap.map { case (k, v) => (k, dynamicValueFromJson(v)) }.toList: _*),
      )
  }

  private[circe] def dynamicValueFromJson(json: Json): DynamicValue = json.foldWith(dynamicValueFolder)
}
