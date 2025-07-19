package zio.schema.codec.circe.internal

import zio.schema.NameFormat
import zio.schema.codec.circe.{DiscriminatorSetting, ExplicitConfig}

/**
 * Internal circe codec configuration. The configurations are overruled by the
 * annotations that configure the same behavior.
 *
 * @param explicitEmptyCollections
 *   whether to encode empty collections as `[]` or omit the field and decode
 *   the field when it is missing as an empty collection or fail
 * @param explicitNulls
 *   whether to encode empty Options as `null` or omit the field and decode the
 *   field when it is missing to None or fail
 * @param discriminatorSettings
 *   set up how to handle discriminators
 * @param fieldNameFormat
 *   format for the field names
 * @param rejectExtraFields
 *   whether to reject extra fields during decoding
 */
private[circe] trait Configuration {

  def explicitEmptyCollections: ExplicitConfig
  def explicitNullValues: ExplicitConfig
  def discriminatorSettings: DiscriminatorSetting
  def fieldNameFormat: NameFormat
  def rejectExtraFields: Boolean

  val noDiscriminator: Boolean = discriminatorSettings match {
    case DiscriminatorSetting.NoDiscriminator => true
    case _                                    => false
  }

  val discriminatorName: Option[String] = discriminatorSettings match {
    case DiscriminatorSetting.Name(name, _) => Some(name)
    case _                                  => None
  }

  val discriminatorFormat: NameFormat = discriminatorSettings match {
    case DiscriminatorSetting.ClassName(format) => format
    case DiscriminatorSetting.Name(_, format)   => format
    case _                                      => NameFormat.Identity
  }
}
