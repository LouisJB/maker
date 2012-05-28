package maker.task

object DependencyTree{
  def apply[A](as : Set[A], getChildren : A ⇒ Set[A]) : DependencyTree[A] = DependencyTree(as.map{
    a ⇒ a → getChildren(a)
  }.toMap)
}

case class DependencyTree[A](tree : Map[A, Set[A]]){
  def all : Set[A] = tree.flatMap{
    case (parent, children) ⇒ children + parent
  }.toSet

  def parents : Set[A] = tree.map{
    case (parent, _) ⇒ parent
  }.toSet

  def childless : Set[A] = all.filterNot(parents)

  def +(parent : A, children : Set[A]) : DependencyTree[A] = {
    val singletonTree : DependencyTree[A] = DependencyTree(Map[A, Set[A]](parent → children))
    this ++ singletonTree
  }

  def ++ (otherTree : DependencyTree[A]) : DependencyTree[A] = {
    var mergedTree = tree
    otherTree.tree.foreach{
      case (parent, children) ⇒
        mergedTree = mergedTree.updated(parent, mergedTree.getOrElse(parent, Set[A]()))
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
}
