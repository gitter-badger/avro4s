package com.sksamuel.avro4s

import org.apache.avro.Schema

import scala.language.experimental.macros
import scala.language.{higherKinds, implicitConversions}
import scala.reflect.macros.Context

trait AvroSchema[T] {
  def schema: org.apache.avro.Schema
  def props: Map[String, String] = Map.empty
}

object SchemaMacros {

  implicit val StringSchema: AvroSchema[String] = new AvroSchema[String] {
    def schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit val IntSchema: AvroSchema[Int] = new AvroSchema[Int] {
    def schema: Schema = Schema.create(Schema.Type.INT)
  }

  implicit val LongSchema: AvroSchema[Long] = new AvroSchema[Long] {
    def schema: Schema = Schema.create(Schema.Type.LONG)
  }

  implicit val BooleanSchema: AvroSchema[Boolean] = new AvroSchema[Boolean] {
    def schema: Schema = Schema.create(Schema.Type.BOOLEAN)
  }

  implicit val FloatSchema: AvroSchema[Float] = new AvroSchema[Float] {
    def schema: Schema = Schema.create(Schema.Type.FLOAT)
  }

  implicit val ByteArraySchema: AvroSchema[Array[Byte]] = new AvroSchema[Array[Byte]] {
    def schema: Schema = Schema.create(Schema.Type.BYTES)
  }

  implicit val DoubleSchema: AvroSchema[Double] = new AvroSchema[Double] {
    def schema: Schema = Schema.create(Schema.Type.DOUBLE)
  }

  implicit val BigDecimalSchema: AvroSchema[BigDecimal] = new AvroSchema[BigDecimal] {
    def schema: Schema = Schema.create(Schema.Type.DOUBLE)
    override def props: Map[String, String] = Map("logicalType" -> "decimal", "precision" -> "4", "scale" -> "2")
  }

  implicit def ArraySchema[S](implicit subschema: AvroSchema[S]): AvroSchema[Array[S]] = {
    new AvroSchema[Array[S]] {
      def schema: Schema = Schema.createArray(subschema.schema)
    }
  }

  implicit def IterableSchema[S](implicit subschema: AvroSchema[S]): AvroSchema[Iterable[S]] = {
    new AvroSchema[Iterable[S]] {
      def schema: Schema = Schema.createArray(subschema.schema)
    }
  }

  implicit def ListSchema[S](implicit subschema: AvroSchema[S]): AvroSchema[List[S]] = {
    new AvroSchema[List[S]] {
      def schema: Schema = Schema.createArray(subschema.schema)
    }
  }

  implicit def SeqSchema[S](implicit subschema: AvroSchema[S]): AvroSchema[Seq[S]] = {
    new AvroSchema[Seq[S]] {
      def schema: Schema = Schema.createArray(subschema.schema)
    }
  }

  implicit def MapSchema[V](implicit valueSchema: AvroSchema[V]): AvroSchema[Map[String, V]] = {
    new AvroSchema[Map[String, V]] {
      def schema: Schema = Schema.createMap(valueSchema.schema)
    }
  }

  def fieldBuilder[T](name: String)(implicit schema: AvroSchema[T]): Schema.Field = {
    val field = new Schema.Field(name, schema.schema, null, null)
    schema.props.foreach { case (k, v) => field.addProp(k, v) }
    field
  }

  def schemaImpl[T: c.WeakTypeTag](c: Context): c.Expr[AvroSchema[T]] = {

    import c.universe._
    val t = weakTypeOf[T]

    val fields = t.declarations.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.get.paramss.head

    val name = t.typeSymbol.fullName

    val fieldSchemaPartTrees: Seq[Tree] = fields.map { f =>
      val name = f.name.decoded
      val sig = f.typeSignature
      q"""{import com.sksamuel.avro4s.SchemaMacros._; fieldBuilder[$sig]($name)}"""
    }

    c.Expr[AvroSchema[T]]( q"""
      new com.sksamuel.avro4s.AvroSchema[$t] {
        def schema = {
         import scala.collection.JavaConverters._
         val s = org.apache.avro.Schema.createRecord($name, null, $name, false)
         val fields = Seq(..$fieldSchemaPartTrees)
         s.setFields(fields.asJava)
         s
        }
      }
    """)
  }
}