package maker.task

object DependencyTree{
  def apply[A](as : Set[A], getChildren : A ⇒ Set[A]) : DependencyTree[A] = DependencyTree(as.map{
    a ⇒ a → getChildren(a)
  }.toMap)
}

case class DependencyTree[A](tree : Map[A, Set[A]] = Map[A, Set[A]]()){
  def all : Set[A] = tree.flatMap{
    case (parent, children) ⇒ children + parent
  }.toSet

  def parents : Set[A] = tree.map{
    case (parent, _) ⇒ parent
  }.toSet

  def childless : Set[A] = {
    val parentsWithChildren = parents.filter(tree(_).size > 0)
    all.filterNot(parentsWithChildren)
  }

  def - (a : A) = filter(_ != a)

  def +(parent : A, children : Set[A]) : DependencyTree[A] = {
    val singletonTree : DependencyTree[A] = DependencyTree(Map[A, Set[A]](parent → children))
    this ++ singletonTree
  }

  def ++ (otherTree : DependencyTree[A]) : DependencyTree[A] = {
    var mergedTree = tree
    otherTree.tree.foreach{
      case (parent, children) ⇒
        mergedTree = mergedTree.updated(parent, children ++ mergedTree.getOrElse(parent, Set[A]()))
    }
    DependencyTree(mergedTree)
  }
    

  def filter(predicate : A ⇒ Boolean) : DependencyTree[A] = {
    val filtered = tree.filterKeys(predicate).mapValues(_.filter(predicate))
    val orphansToKeep = all.filter(predicate).map{
      o ⇒ (o, Set[A]())
    }.toMap

    DependencyTree(filtered) ++ DependencyTree(orphansToKeep)
  }

  override def toString = {
    def sortedByName(as : Iterable[A]) : List[A] = {
      as.toList.sortWith(_.toString < _.toString)
    }
    val b = new StringBuffer
    b.append("\n")
    sortedByName(tree.keys).foreach{
      key ⇒ 
        b.append(key + "\n")
        sortedByName(tree(key)).foreach{
          value ⇒ 
            b.append("\t" + value + "\n")
        }
        
    }
    b.toString
  }
}
