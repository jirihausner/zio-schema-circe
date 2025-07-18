package zio.schema.codec.circe

/**
 * When disabled for encoding, matching fields will be omitted from the JSON.
 * When disabled for decoding, missing fields will be decoded as default value.
 */
final case class ExplicitConfig(encoding: Boolean = true, decoding: Boolean = false)
