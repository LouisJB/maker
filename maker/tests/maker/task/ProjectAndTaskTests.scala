package maker.task

import maker.utils.FileUtils._
import maker.task.tasks._
import org.scalatest.FunSuite
import maker.project.Project

class ProjectAndTaskTests extends FunSuite{
  private def toDependencyTree(map : Map[(Project, Task), Set[(Project, Task)]]) = {
    val newMap = map.map{
      case ((p, t), set) ⇒ 
        ProjectAndTask(p, t) → set.map{
          case (p, t) ⇒ ProjectAndTask(p, t)
        }
    }
    DependencyTree(newMap)
  }
  withTempDir {
    dir ⇒ 
      val childProject = Project("child", file(dir, "child"))
      val parentProject = Project("parent", file(dir, "parent")) dependsOn childProject
      val expectedDependencies = Map[(Project, Task), Set[(Project, Task)]](
        (parentProject, RunUnitTestsTask) → Set((parentProject, CompileTestsTask)),
        (parentProject, CompileTestsTask) → Set((parentProject, CompileSourceTask), (childProject, CompileTestsTask)),
        (parentProject, CompileSourceTask) → Set((childProject, CompileSourceTask)),
        (childProject, CompileTestsTask) → Set((childProject, CompileSourceTask))
      )
      val tree = ProjectAndTask(parentProject, RunUnitTestsTask).dependencyTree
      
      assert(tree === toDependencyTree(expectedDependencies))

  }
}
