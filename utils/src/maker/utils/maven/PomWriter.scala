package maker.utils.maven

import maker.utils._
import java.io.File
import scala.xml._

case class MavenRepository(id : String, name : String, url : String, layout : String)
case class ProjectDef(description : String, moduleLibDef : DependencyLib, repos : List[MavenRepository])
case class ModuleDef(projectDef : ProjectDef, dependencies : List[DependencyLib], repositories : List[MavenRepository])

object PomWriter {

  def writePom(file : File, moduleDef : ModuleDef) {

    val pomOuter =
        //<?xml version="1.0" encoding=\'UTF-8\'?>
        <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.  org/POM/4.0.0">
            <groupId>{moduleDef.projectDef.moduleLibDef.name}</groupId>
            <artifactId>todo</artifactId>
            <packaging>jar</packaging>
            <description>{moduleDef.projectDef.description}</description>
            <version>{moduleDef.projectDef.moduleLibDef.version}</version>
            <name>{moduleDef.projectDef.moduleLibDef.name}</name>
            <organization>
                <name>{moduleDef.projectDef.moduleLibDef.org}</name>
            </organization>
            mkDependencies(moduleDef.dependencies)
            mkRepostitories(moduleDef.repositories)
        </project>

     println("xml= \n" + pomOuter)
     pomOuter
  }

  private def mkDependencies(dependencies : List[DependencyLib]) : NodeSeq = {
    <dependencies>{
      dependencies.map(d =>
        <dependency>
          <groupId>{d.name}</groupId>
          <artifactId>{d.name}</artifactId>
          <version>{d.version}</version>
          <scope>comple (todo)</scope>
        </dependency>)}
    </dependencies>
  }

  private def mkRepositories(repositories : List[MavenRepository]) : NodeSeq = {
    <repositories>{
    repositories.map(r =>
      <repository>
        <id>{r.id}</id>
        <name>{r.name}</name>
        <url>{r.url}</url>
        <layout>{r.layout}</layout>
      </repository>)}
    </repositories>
  }
}
