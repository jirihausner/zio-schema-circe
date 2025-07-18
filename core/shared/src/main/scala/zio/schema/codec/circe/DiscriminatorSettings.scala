package zio.schema.codec.circe

import zio.schema.NameFormat

sealed trait DiscriminatorSetting

object DiscriminatorSetting {

  val default: ClassName = ClassName(NameFormat.Identity)

  case class ClassName(format: NameFormat)                                extends DiscriminatorSetting
  case object NoDiscriminator                                             extends DiscriminatorSetting
  case class Name(name: String, format: NameFormat = NameFormat.Identity) extends DiscriminatorSetting
}
