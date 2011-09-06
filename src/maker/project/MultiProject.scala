package maker.project

case class MultiProject(projects : Project*){
  def clean = projects.par.foreach(_.clean)
  def compile = projects.par.foreach(_.compile)
}
