package com.intellij.flex.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

public class ShareableFlexConfigGenerator extends IdeaConfigurator {
  private static final String TARGET_WITH_FRONT_SLASH = "/target";
  private static final String CLASSSES_WITH_FRONT_SLASH = "/target/classes";

  private static boolean listForGantGenerated;

  private String localRepositoryBasedir;
  private int localRepositoryBasedirLength;
  
  private String baseDir;

  public ShareableFlexConfigGenerator(MavenSession session, File outputDirectory) {
    super(session, new File("build-gant/flex-configs"));
    //noinspection ResultOfMethodCallIgnored
    this.outputDirectory.mkdirs();
  }

  @Override
  public void preGenerate(MavenProject project, String classifier) throws IOException {
    super.preGenerate(project, classifier);

    baseDir = project.getBasedir().getPath();

    localRepositoryBasedir = session.getLocalRepository().getBasedir();
    localRepositoryBasedirLength = localRepositoryBasedir.length();

    if (!listForGantGenerated) {
      listForGantGenerated = true;

      StringBuilder s = new StringBuilder(8192);
      s.append("def List<SwfDescriptor> getList() {\n\treturn [");

      boolean isFirst = true;
      final int absPathPrefixLength = session.getTopLevelProject().getBasedir().getPath().length();
      for (MavenProject p : session.getProjects()) {
        final String packaging = p.getPackaging();
        if (packaging.equals("swf") || packaging.equals("air") || packaging.equals("swc")) {
          if (!isFirst) {
            s.append(", ");
          }
          else {
            isFirst = false;
          }

          s.append("\n\t\tnew SwfDescriptor(\"").append(p.getArtifactId()).append(".xml\", \"");
          final String pBaseDir = p.getBasedir().getPath();
          if (session.getTopLevelProject() != p) {
            s.append(pBaseDir.substring(absPathPrefixLength + 1));
          }
          else {
            s.append(pBaseDir);
          }
          s.append("\", ").append(packaging.equals("swc") ? "true" : "false").append(')');
        }
      }

      s.append("\n\t];\n}");
      Utils.write(s, new File("build-gant/flex_ui_designer_swfs_build.gant"));
    }
  }

  @Override
  protected String getConfigFilePath(MavenProject project, String classifier) {
    return project.getArtifactId() + ".xml";
  }

  @Override
  protected void processValue(String value, String name) throws IOException {
    if (name.equals(LOCAL_FONTS_SNAPSHOT)) {
      out.append("@@repo@@/fonts.ser");
    }
    else if (name.equals("output")) {
      target(value);
    }
    else {
      super.processValue(value, name);
    }
  }

  @Override
  protected void writeTag(String indent, String name, String value, String parentName) throws IOException {
    if (name.equals(PATH_ELEMENT) || name.equals("library") || name.equals("manifest") || name.equals("path")) {
      out.append(indent).append("\t<").append(name).append('>');

      if (parentName.equals(FILE_SPECS) || parentName.equals("source-path") || parentName.equals("include-sources") || parentName.equals("include-file")) {
        if (value.startsWith(baseDir)) {
          out.append("@@baseDir@@").append(value, baseDir.length(), value.length());
        }
        else {
          int sIndex = value.indexOf(TARGET_WITH_FRONT_SLASH);
          if (sIndex != -1) {
            out.append("@@target@@").append(value, sIndex + TARGET_WITH_FRONT_SLASH.length(), value.length());
          }
          else {
            throw new IllegalArgumentException(value);
          }
        }
      }
      else {
        int sIndex;
        if (value.startsWith(localRepositoryBasedir)) {
          out.append("@@repo@@").append(value, localRepositoryBasedirLength, value.length());
        }
        else if ((sIndex = value.indexOf(CLASSSES_WITH_FRONT_SLASH)) != -1) {
          String filename = value.substring(sIndex + CLASSSES_WITH_FRONT_SLASH.length() + 1);
          Utils.copyFile(new File(value), new File("build-gant/flex-configs/" + filename));
          out.append("@@configs@@/").append(filename);
        }
        else {
          target(value);
        }
      }
      
      out.append("</").append(name).append('>');
    }
    else {
      if (name.equals(LOCAL_FONTS_SNAPSHOT)) {
        value = "@@repo@@/fonts.ser";
      }
      super.writeTag(indent, name, value, parentName);
    }
  }

  private void target(String value) {
    out.append("@@target@@").append(value, lastSlashIndex(value), value.length());
  }

  private static int lastSlashIndex(String value) {
    int index = value.lastIndexOf('/');
    return index == -1 ? value.lastIndexOf('\\') : index;
  }
}