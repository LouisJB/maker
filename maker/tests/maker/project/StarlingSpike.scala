package maker.project

import java.io.File

object StarlingSpike extends App{
  val starlingRoot = new File("/home/alex/repos/dev/services/starling/")
  val utilsRoot  = new File(starlingRoot, "utils")
  val utils = Project(
    "utils",
    utilsRoot,
    List(new File(utilsRoot, "src")),
    List(new File(utilsRoot, "lib"), new File(utilsRoot, "lib_managed")),
    new File(utilsRoot, "out"),
    new File(utilsRoot, "out")
  )

  val compResult = utils.compile
  println(compResult)

    
}
