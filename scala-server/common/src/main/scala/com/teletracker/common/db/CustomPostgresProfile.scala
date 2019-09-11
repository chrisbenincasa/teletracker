package com.teletracker.common.db

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import io.circe.Json
import io.circe.parser.parse
import slick.basic.Capability
import slick.jdbc.JdbcType

trait CustomPostgresProfile
    extends ExPostgresProfile
    with PgArraySupport
    with PgAggFuncSupport
    with PgDate2Support
    with PgRangeSupport
    with PgHStoreSupport
    with PgSearchSupport
    with PgNetSupport
    with PgCirceJsonSupport
    with PgLTreeSupport {

  import slick.ast._
  import slick.ast.Library._
  import slick.lifted.FunctionSymbolExtensionMethods._

  def pgjson =
    "jsonb" // jsonb support is in postgres 9.4.0 onward; for 9.3.x use "json"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate

  override val api = CustomAPI

  object CustomAPI
      extends API
      with ArrayImplicits
      with DateTimeImplicits
      with NetImplicits
      with LTreeImplicits
      with RangeImplicits
      with HStoreImplicits
      with SearchImplicits
      with JsonImplicits
      with SearchAssistants {

    import io.circe.syntax._

    implicit val strListTypeMapper =
      new SimpleArrayJdbcType[String]("text").to(_.toList)
    implicit override val circeJsonTypeMapper: JdbcType[Json] =
      new GenericJdbcType[Json](
        pgjson,
        (v) => parse(v).getOrElse(Json.Null),
        (v) => v.asJson.noSpaces,
        hasLiteralForm = false
      )

    // Declare the name of an aggregate function:
    val ArrayAgg = new SqlAggregateFunction("array_agg")

    // Implement the aggregate function as an extension method:
    implicit class ArrayAggColumnQueryExtensionMethods[P, C[_]](
      val q: Query[Rep[P], _, C]) {
      def arrayAgg[B](implicit tm: TypedType[List[B]]) =
        ArrayAgg.column[List[B]](q.toNode)
    }
  }
}

object CustomPostgresProfile extends CustomPostgresProfile
