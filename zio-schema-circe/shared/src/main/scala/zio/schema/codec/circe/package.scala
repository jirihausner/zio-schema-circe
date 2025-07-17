package zio.schema.codec

import io.circe.{Json, JsonNumber, JsonObject}
import zio.schema.Schema
import zio.schema.annotation.directDynamicMapping
import zio.schema.codec.circe.internal._

package object circe {

  implicit val schemaJson: Schema[Json] =
    Schema.dynamicValue
      .transform(dynamicValueToJson, dynamicValueFromJson)
      .annotate(directDynamicMapping())

  implicit val schemaJsonObject: Schema[JsonObject] =
    Schema.dynamicValue
      .transform(dynamicValueToJsonObject, (obj: JsonObject) => dynamicValueFromJson(obj.toJson))
      .annotate(directDynamicMapping())

  implicit val schemaJsonNumber: Schema[JsonNumber] =
    Schema.dynamicValue
      .transformOrFail(
        dynamicValueToJsonNumber,
        (number: JsonNumber) => Right(dynamicValueFromJson(Json.fromJsonNumber(number))),
      )
      .annotate(directDynamicMapping())
}
