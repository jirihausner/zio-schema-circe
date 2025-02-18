package zio.schema.codec.circe.internal

import io.circe._
import zio.Chunk
import zio.prelude.NonEmptyMap
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.circe.CirceCodec

import java.time.temporal.Temporal
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ListMap
import scala.collection.mutable

private[circe] object Codecs extends Codecs

private[circe] trait Codecs {

  private val EmptyJsonObj: Json = Json.obj()

  type DiscriminatorTuple = Option[(String, String)]

  def encodeChunk[A](implicit encoder: Encoder[A]): Encoder.AsArray[Chunk[A]] = new Encoder.AsArray[Chunk[A]] {

    final def encodeArray(chunk: Chunk[A]): Vector[Json] = {
      val builder  = Vector.newBuilder[Json]
      val iterator = chunk.iterator
      while (iterator.hasNext) {
        builder += encoder(iterator.next())
      }
      builder.result()
    }
  }

  def decodeChunk[A](implicit decoder: Decoder[A]): Decoder[Chunk[A]] =
    Decoder.decodeVector(decoder).map(Chunk.fromIterable)

  def encodeSuspend[A](encoder: => Encoder[A]): Encoder[A] = new Encoder[A] {
    lazy val underlying: Encoder[A] = encoder
    def apply(a: A): Json           = underlying(a)
  }

  def decodeSuspend[A](decoder: => Decoder[A]): Decoder[A] = new Decoder[A] {
    lazy val underlying: Decoder[A]          = decoder
    def apply(c: HCursor): Decoder.Result[A] = underlying(c)
  }

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

  private case class EncoderKey[A](
    schema: Schema[A],
    config: CirceCodec.Config,
    discriminatorTuple: DiscriminatorTuple,
  ) {
    override val hashCode: Int = System.identityHashCode(schema) ^ config.hashCode ^ discriminatorTuple.hashCode
    override def equals(obj: Any): Boolean = obj match {
      case x: EncoderKey[_] => (x.schema eq schema) && x.config == config && x.discriminatorTuple == discriminatorTuple
      case _                => false
    }
  }

  private case class DecoderKey[A](schema: Schema[A], discriminator: Option[String]) {
    override val hashCode: Int             = System.identityHashCode(schema) ^ discriminator.hashCode
    override def equals(obj: Any): Boolean = obj match {
      case x: DecoderKey[_] => (x.schema eq schema) && x.discriminator == discriminator
      case _                => false
    }
  }

  private val encoders = new ConcurrentHashMap[EncoderKey[_], Encoder[_]]()
  private val decoders = new ConcurrentHashMap[DecoderKey[_], Decoder[_]]()

  def encodeSchema[A](
    schema: Schema[A],
    config: CirceCodec.Config,
    discriminatorTuple: DiscriminatorTuple = None,
  ): Encoder[A] = {
    val key                 = EncoderKey(schema, config, discriminatorTuple)
    var encoder: Encoder[A] = encoders.get(key).asInstanceOf[Encoder[A]]
    if (encoder eq null) {
      encoder = encodeSchemaSlow(schema, config, discriminatorTuple)
      encoders.put(key, encoder)
    }
    encoder
  }

  def decodeSchema[A](schema: Schema[A], discriminator: Option[String] = None): Decoder[A] = {
    val key     = DecoderKey(schema, discriminator)
    var decoder = decoders.get(key).asInstanceOf[Decoder[A]]
    if (decoder eq null) {
      decoder = decodeSchemaSlow(schema, discriminator)
      decoders.put(key, decoder)
    }
    decoder
  }

  def encodeSchemaSlow[A](
    schema: Schema[A],
    config: CirceCodec.Config,
    discriminatorTuple: DiscriminatorTuple = None,
  ): Encoder[A] = schema match {
    case Schema.Primitive(standardType, _)   => encodePrimitive(standardType)
    case Schema.Optional(schema, _)          => Encoder.encodeOption(encodeSchema(schema, config))
    case Schema.Tuple2(l, r, _)              => Encoder.encodeTuple2(encodeSchema(l, config), encodeSchema(r, config))
    case Schema.Sequence(schema, _, g, _, _) => encodeChunk(encodeSchema(schema, config)).contramap(g)
    case Schema.NonEmptySequence(schema, _, g, _, _) => encodeChunk(encodeSchema(schema, config)).contramap(g)
    case Schema.Map(ks, vs, _)                       => encodeMap(ks, vs, config)
    case Schema.NonEmptyMap(ks, vs, _)               => encodeMap(ks, vs, config).contramap(_.toMap)
    case Schema.Set(s, _)                            => Encoder.encodeSet(encodeSchema(s, config))
    case Schema.Transform(c, _, g, a, _)             =>
      encodeTransform(a.foldLeft(c)((s, a) => s.annotate(a)), g, config, discriminatorTuple)
    case Schema.Fail(_, _)                           => Encoder.encodeUnit.contramap(_ => ())
    case Schema.Either(left, right, _)               =>
      Encoder.encodeEither("Left", "Right")(encodeSchema(left, config), encodeSchema(right, config))
    case Schema.Fallback(left, right, _, _) => encodeFallback(encodeSchema(left, config), encodeSchema(right, config))
    case l @ Schema.Lazy(_)                 => encodeSuspend(encodeSchema(l.schema, config))
    case s: Schema.GenericRecord            => encodeRecord(s, config, discriminatorTuple)
    case s: Schema.Record[A]                => encodeCaseClass(s, config, discriminatorTuple)
    case s: Schema.Enum[A]                  => encodeEnum(s, config)
    case d @ Schema.Dynamic(_)              => encodeDynamic(d, config)
    case null                               =>
      throw new Exception(s"A captured schema is null, most likely due to wrong field initialization order")
  }

  def decodeSchemaSlow[A](schema: Schema[A], discriminator: Option[String] = None): Decoder[A] = schema match {
    case Schema.Primitive(standardType, _)              => decodePrimitive(standardType)
    case Schema.Optional(codec, _)                      => Decoder.decodeOption(decodeSchema(codec))
    case Schema.Tuple2(left, right, _)                  => Decoder.decodeTuple2(decodeSchema(left), decodeSchema(right))
    case Schema.Sequence(codec, f, _, _, _)             => decodeChunk(decodeSchema(codec)).map(f)
    case s @ Schema.NonEmptySequence(codec, _, _, _, _) => decodeChunk(decodeSchema(codec)).map(s.fromChunk)
    case Schema.Map(ks, vs, _)                          => decodeMap(ks, vs)
    case Schema.NonEmptyMap(ks, vs, _)                  =>
      decodeMap(ks, vs).emap(m => NonEmptyMap.fromMapOption(m).toRight("NonEmptyMap expected"))
    case Schema.Set(s, _)                               => Decoder.decodeSet(decodeSchema(s))
    case Schema.Transform(c, f, _, a, _)                =>
      decodeSchema(a.foldLeft(c)((s, a) => s.annotate(a)), discriminator).emap(f)
    case Schema.Fail(message, _)                        => decodeFail(message)
    case Schema.Either(left, right, _)                  =>
      Decoder.decodeEither("Left", "Right")(decodeSchema(left), decodeSchema(right))
    case s @ Schema.Fallback(_, _, _, _)                => decodeFallback(s)
    case l @ Schema.Lazy(_)                             => decodeSuspend(decodeSchema(l.schema))
    case s: Schema.GenericRecord                        => decodeRecord(s, discriminator)
    case s @ Schema.CaseClass0(_, _, _)                 => decodeCaseClass0(s, discriminator)
    case s @ Schema.CaseClass1(_, _, _, _)              => decodeCaseClass1(s, discriminator)
    case s @ Schema.CaseClass2(_, _, _, _, _)           => decodeCaseClass2(s, discriminator)
    case s @ Schema.CaseClass3(_, _, _, _, _, _)        => decodeCaseClass3(s, discriminator)
    case s @ Schema.CaseClass4(_, _, _, _, _, _, _)     => decodeCaseClass4(s, discriminator)
    case s @ Schema.CaseClass5(_, _, _, _, _, _, _, _)  => decodeCaseClass5(s, discriminator)
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
    case s @ Schema.CaseClass16(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)          =>
      decodeCaseClass16(s, discriminator)
    case s @ Schema.CaseClass17(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)       =>
      decodeCaseClass17(s, discriminator)
    case s @ Schema.CaseClass18(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)    =>
      decodeCaseClass18(s, discriminator)
    case s @ Schema.CaseClass19(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass19(s, discriminator)
    case s @ Schema.CaseClass20(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass20(s, discriminator)
    case s @ Schema.CaseClass21(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass21(s, discriminator)
    case s @ Schema.CaseClass22(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
      decodeCaseClass22(s, discriminator)
    case s: Schema.Enum[A]                                                                        => decodeEnum(s)
    case d @ Schema.Dynamic(_)                                                                    => decodeDynamic(d)
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
  ): Encoder[Map[K, V]] = encodeField(ks) match {
    case Some(keyEncoder) => Encoder.encodeMap(keyEncoder, encodeSchema(vs, config))
    case None             =>
      encodeChunk(Encoder.encodeTuple2(encodeSchema(ks, config), encodeSchema(vs, config)))
        .contramap(Chunk.fromIterable)
  }

  def decodeMap[K, V](ks: Schema[K], vs: Schema[V]): Decoder[Map[K, V]] = decodeField(ks) match {
    case Some(keyDecoder) => Decoder.decodeMap(keyDecoder, decodeSchema(vs))
    case None             =>
      decodeChunk(Decoder.decodeTuple2(decodeSchema(ks), decodeSchema(vs)))
        .map(_.toMap)
  }

  private def isEmptyJsonArray(json: Json): Boolean = json.asArray.map(_.isEmpty).getOrElse(false)

  def encodeDynamic(schema: Schema.Dynamic, config: CirceCodec.Config): Encoder[DynamicValue] = {
    if (schema.annotations.exists(_.isInstanceOf[directDynamicMapping])) {
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
    discriminatorTuple: DiscriminatorTuple,
  ): Encoder[B] = new Encoder[B] {
    override def apply(b: B): Json = g(b) match {
      case Left(_)  => Json.Null
      case Right(a) => encodeSchema(schema, config, discriminatorTuple)(a)
    }
  }

  def encodeEnum[Z](schema: Schema.Enum[Z], config: CirceCodec.Config): Encoder[Z] = {
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

        val discriminatorName    =
          if (schema.noDiscriminator) None
          else schema.annotations.collectFirst { case d: discriminatorName => d.tag }
        val cases                = schema.nonTransientCases.toArray
        val encoders             = cases.map { case_ =>
          val discriminatorTuple = discriminatorName.map(_ -> case_.caseName)
          encodeSchema(case_.schema.asInstanceOf[Schema[Any]], config, discriminatorTuple)
        }
        val doJsonObjectWrapping = discriminatorName.isEmpty && !schema.noDiscriminator

        override def apply(value: Z): Json = {
          var i = 0
          while (i < cases.size) {
            val case_ = cases(i)
            if (case_.isCase(value)) {
              val result = encoders(i)(case_.deconstruct(value))
              return {
                if (doJsonObjectWrapping) Json.obj(case_.caseName -> result)
                else result
              }
            }
            i += 1
          }
          EmptyJsonObj // for transient cases
        }
      }
    }
  }

  def decodeEnum[Z](parentSchema: Schema.Enum[Z]): Decoder[Z] = {
    val caseNameAliases = new mutable.HashMap[String, Schema.Case[Z, Any]]
    parentSchema.cases.foreach { case_ =>
      val schema = case_.asInstanceOf[Schema.Case[Z, Any]]
      caseNameAliases.put(schema.caseName, schema)
      schema.caseNameAliases.foreach { alias => caseNameAliases.put(alias, schema) }
    }

    if (parentSchema.cases.forall(_.schema.isInstanceOf[Schema.CaseClass0[_]])) { // if all cases are CaseClass0, decode as String
      new Decoder[Z] {

        val cases = new util.HashMap[String, Z](caseNameAliases.size << 1)
        caseNameAliases.foreach { case (name, case_) =>
          cases.put(name, case_.schema.asInstanceOf[Schema.CaseClass0[Z]].defaultConstruct())
        }

        override def apply(c: HCursor): Decoder.Result[Z] = {
          c.as[String].flatMap { str =>
            val result = cases.get(str)
            if (result == null) Left(DecodingFailure(s"Unrecognized subtype: $str", c.history))
            else Right(result)
          }
        }
      }
    } else if (parentSchema.annotations.exists(_.isInstanceOf[noDiscriminator])) {
      new Decoder[Z] {

        val decoders = parentSchema.cases.toArray.map(c => decodeSchema(c.schema))

        override def apply(c: HCursor): Decoder.Result[Z] = {
          val it = decoders.iterator
          while (it.hasNext) {
            it.next().apply(c) match {
              case Left(_)       =>
              case Right(result) =>
                return Right(result.asInstanceOf[Z])
            }
          }
          Left(DecodingFailure("None of the subtypes could decode the data", c.history))
        }
      }
    } else {
      val discriminator = parentSchema.annotations.collectFirst { case d: discriminatorName => d.tag }
      discriminator match {
        case None       =>
          new Decoder[Z] {

            val cases = new util.HashMap[String, Decoder[Any]](caseNameAliases.size << 1)
            caseNameAliases.foreach { case (name, case_) =>
              cases.put(name, decodeSchema(case_.schema, discriminator))
            }

            override def apply(c: HCursor): Decoder.Result[Z] = {
              c.keys.flatMap(_.headOption) match {
                case None        => Left(DecodingFailure("Missing subtype", c.history))
                case Some(field) =>
                  val decoder = cases.get(field)
                  if (decoder == null)
                    Left(DecodingFailure(s"Unrecognized subtype: $field", c.history))
                  else
                    c.get(field)(decoder).map(_.asInstanceOf[Z])
              }
            }
          }
        case Some(name) =>
          new Decoder[Z] {

            val cases = new util.HashMap[String, Decoder[Any]](caseNameAliases.size << 1)
            caseNameAliases.foreach { case (name, case_) =>
              cases.put(name, decodeSchema(case_.schema, discriminator))
            }

            override def apply(c: HCursor): Decoder.Result[Z] = {
              c.downField(name).success match {
                case None         => Left(DecodingFailure(s"Missing subtype: $name", c.history))
                case Some(cursor) =>
                  cursor.as[String] match {
                    case Left(_)    =>
                      Left(DecodingFailure(s"Malformed subtype: ${cursor.value.noSpaces}", cursor.history))
                    case Right(cse) =>
                      val decoder = cases.get(cse)
                      if (decoder == null)
                        Left(DecodingFailure(s"Unrecognized subtype: $cse", cursor.history))
                      else c.as(decoder).map(_.asInstanceOf[Z])
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

    val leftDecoder  = decodeSchema(schema.left)
    val rightDecoder = decodeSchema(schema.right)

    override def apply(c: HCursor): Decoder.Result[Fallback[A, B]] = {
      if (c.value.isArray) {
        c.values.toList.flatten match {
          case Nil                  => Left(DecodingFailure("Empty array on fallback", c.history))
          case left :: right :: Nil =>
            left.as(leftDecoder) match {
              case Left(_)      => right.as(rightDecoder).map(Fallback.Right(_))
              case Right(first) =>
                if (!schema.fullDecode) Right(Fallback.Left(first))
                else
                  right.as(rightDecoder) match {
                    case Left(_)       => Right(Fallback.Left(first))
                    case Right(second) => Right(Fallback.Both(first, second))
                  }
            }
          case _                    => Left(DecodingFailure("Too many items on fallback", c.history))
        }
      } else {
        leftDecoder(c).map(Fallback.Left(_)).left.flatMap { _ =>
          rightDecoder(c).map(Fallback.Right(_)).left.flatMap { _ =>
            decodeFail("Fallback decoder was unable to decode both left and right sides")(c)
          }
        }
      }
    }
  }

  private def encodeRecord[Z](
    schema: Schema.GenericRecord,
    config: CirceCodec.Config,
    discriminatorTuple: DiscriminatorTuple,
  ): Encoder[ListMap[String, _]] = {

    val nonTransientFields = schema.nonTransientFields.toArray
    val discriminator      = discriminatorTuple.map { case (tag, name) =>
      KeyEncoder.encodeKeyString(tag) -> Encoder.encodeString(name)
    }

    if (nonTransientFields.isEmpty) {
      new Encoder[ListMap[String, _]] {

        val result = discriminator match {
          case None        => EmptyJsonObj
          case Some(tuple) => Json.obj(tuple)
        }

        override def apply(value: ListMap[String, _]): Json = result
      }
    } else {
      new Encoder[ListMap[String, _]] {

        val encoders =
          nonTransientFields.map(field => encodeSchema(field.schema.asInstanceOf[Schema[Any]], config))

        override def apply(value: ListMap[String, _]): Json = {
          val builder = Array.newBuilder[(String, Json)]
          discriminator.foreach { case tuple =>
            builder += tuple
          }
          var i       = 0
          while (i < nonTransientFields.length) {
            val field      = nonTransientFields(i)
            val fieldName  = field.fieldName
            val fieldValue = value(fieldName)
            if (!isEmptyOptionalValue(field, fieldValue, config))
              builder += KeyEncoder.encodeKeyString(fieldName) -> encoders(i)(fieldValue)
            i += 1
          }
          Json.fromFields(builder.result())
        }
      }
    }
  }

  private def isEmptyOptionalValue(schema: Schema.Field[_, _], value: Any, config: CirceCodec.Config) = {
    (config.ignoreEmptyCollections || schema.optional) && (value match {
      case None           => true
      case _: Iterable[_] => value.asInstanceOf[Iterable[_]].isEmpty
      case _              => false
    })
  }

  def decodeRecord[Z](
    schema: Schema.GenericRecord,
    discriminator: Option[String],
  ): Decoder[ListMap[String, Any]] = new Decoder[ListMap[String, Any]] {

    val fields                 = schema.fields.toArray
    val fieldWithDecoders      = new util.HashMap[String, (String, Decoder[Any])](schema.fields.size << 1)
    schema.fields.foreach { field =>
      val decoder = decodeSchema(field.schema).asInstanceOf[Decoder[Any]]
      field.nameAndAliases.foreach(fieldWithDecoders.put(_, (field.fieldName, decoder)))
    }
    val rejectAdditionalFields = schema.annotations.exists(_.isInstanceOf[rejectExtraFields])

    override def apply(c: HCursor): Decoder.Result[ListMap[String, Any]] = {
      val map  = new util.HashMap[String, Any](fields.length << 1)
      val keys = c.keys.getOrElse(Seq.empty)

      try {
        keys.foreach { key =>
          val fieldWithDecoder = fieldWithDecoders.get(key)
          if (fieldWithDecoder != null) {
            val (field, decoder) = fieldWithDecoder
            c.get(key)(decoder) match {
              case Left(err)    => throw err
              case Right(value) =>
                val prev = map.put(field, value)
                if (prev != null)
                  throw DecodingFailure(s"Duplicate field: $field", c.history)
            }
          } else {
            if (rejectAdditionalFields && !discriminator.contains(key))
              throw DecodingFailure(s"Unexpected field: $key", c.history)
          }
        }

        var i = 0
        while (i < fields.length) {
          val field = fields(i)
          val name  = field.fieldName
          if (map.get(name) == null) {
            map.put( // mitigation of a linking error for `map.computeIfAbsent` in Scala.js
              name, {
                if ((field.optional || field.transient) && field.defaultValue.isDefined) {
                  field.defaultValue.get
                } else {
                  var schema = field.schema
                  schema match {
                    case l: Schema.Lazy[_] => schema = l.schema
                    case _                 =>
                  }
                  schema match {
                    case collection: Schema.Collection[_, _] => collection.empty
                    case _: Schema.Optional[_]               => None
                    case _                                   =>
                      throw DecodingFailure(s"Missing field: $name", c.history)
                  }
                }
              },
            )
          }
          i += 1
        }

        val builder =
          ListMap.newBuilder[String, Any] ++= ({ // to avoid O(n) insert operations
            import scala.collection.JavaConverters.mapAsScalaMapConverter // use deprecated class for Scala 2.12 compatibility
            map.asScala
          }: @scala.annotation.nowarn)

        Right(builder.result())
      } catch {
        case df: DecodingFailure => Left(df)
        case e: Exception        => Left(DecodingFailure(e.getMessage(), c.history))
      }
    }
  }

  // scalafmt: { maxColumn = 400, optIn.configStyleArguments = false }
  def encodeCaseClass[A](schema: Schema.Record[A], config: CirceCodec.Config, discriminatorTuple: DiscriminatorTuple): Encoder[A] = new Encoder[A] {

    val nonTransientFields = schema.nonTransientFields.map(_.asInstanceOf[Schema.Field[A, Any]]).toArray
    val fieldEncoders      = nonTransientFields.map(s => encodeSchema(s.schema, config, discriminatorTuple))
    val discriminator      = discriminatorTuple.map { case (tag, name) =>
      KeyEncoder.encodeKeyString(tag) -> Encoder.encodeString(name)
    }

    override def apply(a: A): Json = {
      val builder = Array.newBuilder[(String, Json)]
      discriminator.foreach { case tuple =>
        builder += tuple
      }
      var i       = 0
      while (i < nonTransientFields.length) {
        val schema  = nonTransientFields(i)
        val encoder = fieldEncoders(i)
        val value   = schema.get(a)
        val json    = encoder(value)
        if (!isEmptyOptionalValue(schema, value, config) && (!json.isNull || !config.ignoreNullValues))
          builder += KeyEncoder.encodeKeyString(schema.fieldName) -> json
        i += 1
      }
      Json.fromFields(builder.result())
    }
  }

  def decodeCaseClass0[Z](schema: Schema.CaseClass0[Z], discriminator: Option[String]): Decoder[Z] = new Decoder[Z] {

    val rejectExtraFields = schema.annotations.collectFirst { case _: rejectExtraFields => () }.isDefined

    override def apply(c: HCursor): Decoder.Result[Z] = {
      try {
        discriminator match {
          case None       =>
            c.value.asObject match {
              case None      => throw DecodingFailure("Expected object", c.history)
              case Some(obj) =>
                obj.keys.foreach { key =>
                  throw DecodingFailure(s"Extra field: $key", c.history)
                }
            }
          case Some(name) =>
            c.keys.getOrElse(Seq.empty).foreach { key =>
              if (key != name && rejectExtraFields)
                throw DecodingFailure(s"Extra field: $key", c.history)
              c.get[String](key) match {
                case Left(error)  => throw DecodingFailure("Expected string", error.history)
                case Right(value) =>
                  if (value != schema.id.name)
                    throw DecodingFailure(s"Expected ${schema.id.name}", c.history)
              }
            }
        }
      } catch {
        case df: DecodingFailure => return Left(df)
      }
      Right(schema.defaultConstruct())
    }
  }

  def decodeCaseClass1[A, Z](schema: Schema.CaseClass1[A, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.defaultConstruct(buffer(0).asInstanceOf[A])
    }
  }

  def decodeCaseClass2[A1, A2, Z](schema: Schema.CaseClass2[A1, A2, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2])
    }
  }

  def decodeCaseClass3[A1, A2, A3, Z](schema: Schema.CaseClass3[A1, A2, A3, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3])
    }
  }

  def decodeCaseClass4[A1, A2, A3, A4, Z](schema: Schema.CaseClass4[A1, A2, A3, A4, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4])
    }
  }

  def decodeCaseClass5[A1, A2, A3, A4, A5, Z](schema: Schema.CaseClass5[A1, A2, A3, A4, A5, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5])
    }
  }

  def decodeCaseClass6[A1, A2, A3, A4, A5, A6, Z](schema: Schema.CaseClass6[A1, A2, A3, A4, A5, A6, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6])
    }
  }

  def decodeCaseClass7[A1, A2, A3, A4, A5, A6, A7, Z](schema: Schema.CaseClass7[A1, A2, A3, A4, A5, A6, A7, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7])
    }
  }

  def decodeCaseClass8[A1, A2, A3, A4, A5, A6, A7, A8, Z](schema: Schema.CaseClass8[A1, A2, A3, A4, A5, A6, A7, A8, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8])
    }
  }

  def decodeCaseClass9[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z](schema: Schema.CaseClass9[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9])
    }
  }

  def decodeCaseClass10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z](schema: Schema.CaseClass10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10])
    }
  }

  def decodeCaseClass11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z](schema: Schema.CaseClass11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10], buffer(10).asInstanceOf[A11])
    }
  }

  def decodeCaseClass12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z](schema: Schema.CaseClass12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10], buffer(10).asInstanceOf[A11], buffer(11).asInstanceOf[A12])
    }
  }

  def decodeCaseClass13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z](schema: Schema.CaseClass13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z], discriminator: Option[String]): Decoder[Z] = {
    decodeFields(schema, discriminator).map { (buffer: Array[Any]) =>
      schema.construct(buffer(0).asInstanceOf[A1], buffer(1).asInstanceOf[A2], buffer(2).asInstanceOf[A3], buffer(3).asInstanceOf[A4], buffer(4).asInstanceOf[A5], buffer(5).asInstanceOf[A6], buffer(6).asInstanceOf[A7], buffer(7).asInstanceOf[A8], buffer(8).asInstanceOf[A9], buffer(9).asInstanceOf[A10], buffer(10).asInstanceOf[A11], buffer(11).asInstanceOf[A12], buffer(12).asInstanceOf[A13])
    }
  }

  def decodeCaseClass14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z](schema: Schema.CaseClass14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z](schema: Schema.CaseClass15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z](schema: Schema.CaseClass16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, Z](schema: Schema.CaseClass17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, Z](schema: Schema.CaseClass18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, Z](schema: Schema.CaseClass19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, Z](schema: Schema.CaseClass20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, Z](schema: Schema.CaseClass21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, Z], discriminator: Option[String]): Decoder[Z] = {
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

  def decodeCaseClass22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, Z](schema: Schema.CaseClass22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, Z], discriminator: Option[String]): Decoder[Z] = {
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

  private def decodeFields[Z](schema: Schema.Record[Z], discriminator: Option[String]): Decoder[Array[Any]] = new Decoder[Array[Any]] {

    val len      = schema.fields.length
    val fields   = new Array[Schema.Field[Z, _]](len)
    val decoders = new Array[Decoder[_]](len)
    val names    = Array.newBuilder[String]
    val aliases  = new util.HashMap[String, Int](len << 1)

    var i = 0
    schema.fields.foreach { field =>
      fields(i) = field
      decoders(i) = decodeSchema(field.schema)
      val name = field.fieldName
      names += name
      aliases.put(name, i)
      field.annotations.foreach {
        case fna: fieldNameAliases => fna.aliases.foreach(a => aliases.put(a, i))
        case _                     =>
      }
      i += 1
    }

    discriminator.foreach { name =>
      names += name
      aliases.put(name, len)
    }

    val rejectExtraFields = schema.annotations.exists(_.isInstanceOf[rejectExtraFields])

    override def apply(c: HCursor): Decoder.Result[Array[Any]] = {
      val buffer = Array.ofDim[Any](len)
      val keys   = c.keys.getOrElse(Seq.empty)

      try {
        keys.foreach { key =>
          aliases.getOrDefault(key, -1) match {
            case -1                =>
              if (rejectExtraFields)
                throw DecodingFailure(s"Extra field: $key", c.history)
            case idx if idx == len => // check discriminator?
            case idx               =>
              val field = fields(idx).fieldName
              if (buffer(idx) != null) throw DecodingFailure(s"Duplicate field: $field", c.history)
              else
                c.get(key)(decoders(idx)) match {
                  case Left(value)  => throw DecodingFailure(s"Failed to decode field: $field", c.history)
                  case Right(value) => buffer(idx) = value
                }
          }
        }
        var i = 0
        while (i < len) {
          if (buffer(i) == null) {
            val field = fields(i)
            if ((field.optional || field.transient) && field.defaultValue.isDefined) {
              buffer(i) = field.defaultValue.get
            } else {
              var schema = field.schema
              schema match {
                case l: Schema.Lazy[_] => schema = l.schema
                case _                 =>
              }
              schema match {
                case collection: Schema.Collection[_, _] => buffer(i) = collection.empty
                case _: Schema.Optional[_]               => buffer(i) = None
                case _                                   =>
                  throw DecodingFailure(s"Missing field: ${fields(i).name}", c.history)
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
