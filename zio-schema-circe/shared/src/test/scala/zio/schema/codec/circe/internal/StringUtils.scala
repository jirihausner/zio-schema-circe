package zio.schema.codec.circe.internal

private[circe] trait StringUtils {

  def stringify(str: String): String = io.circe.Encoder.encodeString(str).noSpaces
}
