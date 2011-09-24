import java.io.File
import maker.project.Project

val starlingRoot = new File("/home/alex/repos/dev/services/starling/")
val utils = Project(
  "utils",
  new File(starlingRoot, "utils"),
  List(new File(starlingRoot, "utils/src")),
  List(new File(starlingRoot, "utils/lib"), new File(starlingRoot, "utils/lib_managed")),
  new File(starlingRoot, "utils/out"),
  new File(starlingRoot, "utils/out")
)




