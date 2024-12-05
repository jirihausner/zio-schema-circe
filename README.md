# zio-schema-circe

`zio-schema-circe` seamlessly integrates [zio-schema](https://github.com/zio/zio-schema) with the widely used [Circe](https://circe.github.io/circe/) JSON library.

![CI Badge](https://github.com/jirihausner/zio-schema-circe/workflows/CI/badge.svg) ![Maven Central Version](https://img.shields.io/maven-central/v/io.github.jirihausner/zio-schema-circe_2.13) [![ZIO Schema Circe](https://img.shields.io/github/stars/jirihausner/zio-schema-circe?style=social)](https://github.com/jirihausner/zio-schema-circe)

## Why zio-schema-circe?

- Perfect for projects that already use Circe that want to take advantage of the type-safe schema definitions of `zio-schema`.
- Provides an alternative to [zio-schema-json](https://github.com/zio/zio-schema/tree/master/zio-schema-json), catering to teams already invested in Circe's ecosystem.
- Makes it easier to gradually migrate to `zio-schema` or incorporate its features into legacy stacks.

## Installation

In order to use this library, we need to add one (or more) of the following lines in our `build.sbt` file:

```scala
libraryDependencies += "io.github.jirihausner" %% "zio-schema-circe"          % "0.1.0"
libraryDependencies += "io.github.jirihausner" %% "zio-schema-circe-jsoniter" % "0.1.0"
```

## Example

```scala
import io.circe.Codec
import io.circe.syntax._
import io.circe.parser.decode
import zio.schema.codec.circe.CirceCodec
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
    implicit val schema: Schema[Person] = DeriveSchema.gen
}

// derive Circe codes from Schema
implicit val codec: Codec[Person] = CirceCodec.schemaCodec(schema)

decode[Person]("""{"name": "John", "age": 30}""") // Person("John", 30)
Person("Adam", 24).asJson.noSpaces                // {"Adam": 24}

// use existing Circe codecs as BinaryCodec
import io.circe.generic.semiauto.deriveCodec
import zio.schema.codec.circe.CirceCodec.circeBinaryCodec

circeBinaryCodec[Person](deriveCodec) // zio.schema.codec.BinaryCodec[Person]

// derive circe BinaryCodec from schema
import zio.schema.codec.circe.CirceCodec.schemaBasedBinaryCodec

schemaBasedBinaryCodec[Person](CirceCodec.Config.default) // zio.schema.codec.BinaryCodec[Person]
```

## Acknowledgements

This library was heavily inspired by [zio-schema-json](https://github.com/zio/zio-schema/tree/master/zio-schema-json). Huge thanks to its original contributors for laying foundational ideas and implementation, which greatly influenced `zio-schema-circe`.

## Disclaimer

`zio-schema-circe` is not intended to compete with `zio-schema-json`. Instead, it serves as a complementary option for developers who prefer or already use Circe in their stack.

---

Contributions are welcome! If you have suggestions, improvements, or feature requests, feel free to open an issue or a pull request.
