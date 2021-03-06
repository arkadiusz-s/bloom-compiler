package edu.berkeley.cs.boom.bloomscala.typing

import org.kiama.attribution.Attribution._
import org.kiama.util.Messaging
import edu.berkeley.cs.boom.bloomscala.ast._
import edu.berkeley.cs.boom.bloomscala.typing.FieldType._
import org.kiama.rewriting.PositionalRewriter._
import edu.berkeley.cs.boom.bloomscala.parser.BloomPrettyPrinter.pretty
import org.kiama.attribution.Attributable


class Typer(messaging: Messaging) {

  import messaging.message

  /**
   * Assign types to all expressions.
   */
  def resolveTypes(program: Program): Program = {
    program.statements.map(isWellTyped)
    rewrite(everywherebu(assignType))(program)
  }

  private val assignType =
    rule {
      case ut: UnboundType =>
        // An unbound type should appear as a field of an Expr, so
        // grab that expression's type:
        ut.parent match {
          case ce: ColExpr =>
            colType(ce)
          case re: RowExpr =>
            rowType(re)
        }
    }

  private def expectType(x: ColExpr, t: BloomType) {
    if (x->colType != t) message(x, s"Expected ${pretty(t)} but got ${pretty(x->colType)}")
  }

  private lazy val colType: ColExpr => BloomType =
    attr {
      case PlusStatement(a, b, _) =>
        expectType(a, BloomInt)
        expectType(b, BloomInt)
        BloomInt
      case BoundFieldRef(_, _, field) =>
        field.typ
    }

  private lazy val rowType: RowExpr => RecordType =
    attr {
      case RowExpr(colExprs) =>
        RecordType(colExprs.map(_->colType))
    }

  private lazy val rhsSchema: StatementRHS => RecordType =
    attr {
      case mc: MappedCollection =>
        mc.rowExpr->rowType
      case join: JoinedCollections =>
        join.rowExpr->rowType
      case cr: CollectionRef =>
        cr.collection.schema
      case notin @ NotIn(a, b) =>
        if (a.collection.schema != b.collection.schema)
          message(notin, s"notin called with incompatible schemas:\n${pretty(a.collection.schema)}\n${pretty(b.collection.schema)}")
        a.collection.schema
      case argmin @ ArgMin(collection, groupingCols, chooseExpr, funcRef) =>
        if (groupingCols.map(_.field).toSet.size != groupingCols.size)
          message(argmin, "Grouping columns cannot contain duplicates")
        val funcType = funcRef.function.typ
        val funcName = funcRef.name
        if (Unifier.unify(funcType, FunctionTypes.partialOrder(chooseExpr.typ)).isFailure)
          message(argmin, s"expected partial order over ${pretty(chooseExpr.typ)}, but found function '$funcName' of type ${pretty(funcType)}")
        collection.collection.schema
    }


  /**
   * The collections referenced in this subtree.
   */
  private lazy val referencedCollections: Attributable => Set[CollectionDeclaration] =
    attr {
      case cr: CollectionRef => Set(cr.collection)
      case n: Node => n.children.map(referencedCollections).foldLeft(Set.empty[CollectionDeclaration])(_.union(_))
    }

  lazy val isWellTyped: Statement => Boolean =
    attr {
      case stmt @ Statement(lhs, op, rhs, _) =>
        if (!referencedCollections(rhs).forall(c => CollectionType.validRHSTypes.contains(c.collectionType))) {
          message(stmt, s"Output collections cannot appear in the RHS of rules")
          false
        } else if (!CollectionType.validLHSTypes.contains(lhs.collection.collectionType)) {
          message(stmt, s"Cannot insert into collections of type '${lhs.collection.collectionType}'")
          false
        } else if (lhs.collection.collectionType == CollectionType.Output && op != BloomOp.AsynchronousMerge) {
          message(stmt, s"Output collections only support the <~ operator, but found " + op)
          false
        } else {
          val lSchema = lhs.collection.schema
          val rSchema = rhsSchema(rhs)
          if (rSchema != lSchema) {
            message(stmt, s"RHS has wrong schema; expected ${pretty(lSchema)} but got ${pretty(rSchema)}")
            false
          } else {
            true
          }
        }
    }

}
