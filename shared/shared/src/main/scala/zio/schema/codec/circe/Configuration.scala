package zio.schema.codec.circe

import zio.schema.NameFormat

/**
 * When disabled for encoding, matching fields will be omitted from the JSON.
 * When disabled for decoding, missing fields will be decoded as default value.
 */
final case class ExplicitConfig(encoding: Boolean = true, decoding: Boolean = false)

/**
 * Configuration for the JSON codec. The configurations are overruled by the
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
 * @param treatStreamsAsArrays
 *   whether to treat streams as arrays when encoding/decoding
 * @param rejectExtraFields
 *   whether to reject extra fields during decoding
 */
final case class Configuration(
  explicitEmptyCollections: ExplicitConfig = ExplicitConfig(),
  explicitNullValues: ExplicitConfig = ExplicitConfig(),
  discriminatorSettings: DiscriminatorSetting = DiscriminatorSetting.default,
  fieldNameFormat: NameFormat = NameFormat.Identity,
  treatStreamsAsArrays: Boolean = false,
  rejectExtraFields: Boolean = false,
) {
  def withEmptyCollectionsIgnored: Configuration =
    copy(explicitEmptyCollections = ExplicitConfig(encoding = false, decoding = false))

  def withNullValuesIgnored: Configuration =
    copy(explicitNullValues = ExplicitConfig(encoding = false, decoding = false))

  def withNoDiscriminator: Configuration = copy(discriminatorSettings = DiscriminatorSetting.NoDiscriminator)

  def withDiscriminator(format: NameFormat): Configuration =
    copy(discriminatorSettings = DiscriminatorSetting.ClassName(format))

  def withDiscriminator(name: String, format: NameFormat = NameFormat.Identity): Configuration =
    copy(discriminatorSettings = DiscriminatorSetting.Name(name, format))

  def withFieldFormat(format: NameFormat): Configuration = copy(fieldNameFormat = format)

  def withStreamsTreatedAsArrays: Configuration = copy(treatStreamsAsArrays = true)

  def withExtraFieldsSkipped: Configuration = copy(rejectExtraFields = false)

  def withExtraFieldsRejected: Configuration = copy(rejectExtraFields = true)

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

object Configuration {
  val default: Configuration = Configuration()
}

sealed trait DiscriminatorSetting

object DiscriminatorSetting {
  val default: ClassName = ClassName(NameFormat.Identity)
  case class ClassName(format: NameFormat)                                extends DiscriminatorSetting
  case object NoDiscriminator                                             extends DiscriminatorSetting
  case class Name(name: String, format: NameFormat = NameFormat.Identity) extends DiscriminatorSetting
}
