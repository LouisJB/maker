package maker.utils.maven

import maker.utils._
import scala.xml._
import scala.collection.JavaConversions._
import java.io._
import java.net.MalformedURLException
import java.text.ParseException
import org.apache.ivy.plugins.parser.m2.{PomWriterOptions, PomModuleDescriptorWriter}
import org.apache.ivy.Ivy
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.IvyContext

case class ScmDef(url : String, connection : String)
case class MavenRepository(id : String, name : String, url : String, layout : String)

// project peer module related dependencies
case class ProjectDef(description : String,
                      moduleLibDef : DependencyLib,             // this project modules identity as a library
                      dependencyModules : List[DependencyLib])  // the peer modules this project module depends on

// top level module definition
case class ModuleDef(projectDef : ProjectDef,
                     dependencies : List[DependencyLib],        // actual 3'd party dependency libs
                     repositories : List[MavenRepository],      // and the repos they come from
                     scmDef : ScmDef,                           // SCM details
                     licenses : String,
                     developers : String)

object PomWriter {
  def writePom(ivyFile : File,
               ivySettingsFile : File,
               pomFile : File,
               confs : String,
               moduleDef : ModuleDef,
               pomTemplateFile : Option[File]) {

    val ivy = Ivy.newInstance
    ivy.configure(ivySettingsFile)
    val moduleVersion = moduleDef.projectDef.moduleLibDef.version
    ivy.setVariable("maker.module.version", moduleVersion)
    ivy.setVariable("maker.module.groupid", moduleDef.projectDef.moduleLibDef.gav.groupId.id)
    // add necessary 'context' variables for an OSS compliant POM, todo - make all actual properties...
    val contextSettings = IvyContext.getContext().getSettings()
    contextSettings.setVariable("maker.licenses", moduleDef.licenses, true)
    contextSettings.setVariable("maker.scm.url", moduleDef.scmDef.url, true)
    contextSettings.setVariable("maker.scm.connection", moduleDef.scmDef.connection, true)
    contextSettings.setVariable("maker.developers", moduleDef.developers, true)
    val settings = ivy.getSettings
    settings.addAllVariables(System.getProperties)
    Log.debug("In writePom using ivy " + moduleVersion)
    val pomWriterOptions : PomWriterOptions = {
      val deps : List[PomWriterOptions.ExtraDependency] = moduleDef.projectDef.dependencyModules.map(_.toIvyPomWriterExtraDependencies)
      val pwo = ((new PomWriterOptions)
        .setConfs(confs.split(",").map(_.trim))
        .setArtifactName(moduleDef.projectDef.moduleLibDef.name)
        .setArtifactPackaging("jar")
        .setDescription(moduleDef.projectDef.description)
        .setExtraDependencies(deps)
        .setPrintIvyInfo(true))
      pomTemplateFile.map(pt => pwo.setTemplate(pt)).getOrElse(pwo)
    }
    try {
      var md: ModuleDescriptor = XmlModuleDescriptorParser.getInstance.parseDescriptor(settings, ivyFile.toURI.toURL, false)
      Log.debug("about to exec pomModuleDescriptorWriter")
      PomModuleDescriptorWriter.write(md, pomFile, pomWriterOptions)
    }
    catch {
      case e: MalformedURLException => {
        Log.error("unable to convert given ivy file to url: " + ivyFile + ": " + e, e)
      }
      case e: ParseException => {
        Log.error(e.getMessage, e)
      }
      case e: Exception => {
        Log.error("impossible convert given ivy file to pom file: " + e + " from=" + ivyFile + " to=" + pomFile, e)
      }
    }
  }

  def writePom(file : File, moduleDef : ModuleDef) {
    def mkDependencies(dependencies : List[DependencyLib]) : NodeSeq = {
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

    def mkRepositories(repositories : List[MavenRepository]) : NodeSeq = {
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

    val pomOuter =
        //<?xml version="1.0" encoding=\'UTF-8\'?>
        <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.  org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <groupId>{moduleDef.projectDef.moduleLibDef.gav.groupId.id}</groupId>
            <artifactId>{moduleDef.projectDef.moduleLibDef.gav.artifactId.id}</artifactId>
            <packaging>jar</packaging>
            <description>{moduleDef.projectDef.description}</description>
            <version>{moduleDef.projectDef.moduleLibDef.version}</version>
            <name>{moduleDef.projectDef.moduleLibDef.name}</name>
            <organization>
                <name>{moduleDef.projectDef.moduleLibDef.gav.groupId.id}</name>
            </organization>
            {mkDependencies(moduleDef.dependencies)}
            {mkRepositories(moduleDef.repositories)}
        </project>

     println("file " + file.getAbsolutePath  + "\n xml= \n" + pomOuter)

     val pr = new PrintWriter(file)
     pr.println("<?xml version='1.0' encoding='UTF-8'?>")
     pr.println(pomOuter)
     pr.flush()
     pr.close()
     pomOuter
  }
}
