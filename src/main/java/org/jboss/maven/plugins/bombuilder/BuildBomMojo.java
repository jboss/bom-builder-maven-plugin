package org.jboss.maven.plugins.bombuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.codehaus.plexus.util.StringUtils;

/**
 * Build a BOM based on the dependencies in a GAV
 */
@Mojo( name = "build-bom", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE )
public class BuildBomMojo
    extends AbstractMojo
{
    /**
     * BOM groupId
     */
    @Parameter( required = true )
    private String bomGroupId;

    /**
     * BOM artifactId
     */
    @Parameter( required = true )
    private String bomArtifactId;

    /**
     * BOM version
     */
    @Parameter( required = true )
    private String bomVersion;

    /**
     * BOM name
     */
    @Parameter( defaultValue = "" )
    private String bomName;

    /**
     * BOM description
     */
    @Parameter( defaultValue = "" )
    private String bomDescription;

    /**
     * BOM output file
     */
    @Parameter( defaultValue = "bom-pom.xml" )
    private String outputFilename;

    /**
     * Whether the BOM should include the dependency exclusions that
     * are present in the source POM.  By default the exclusions
     * will not be copied to the new BOM.
     */
    @Parameter
    private List<BomExclusion> exclusions;

    /**
     * The current project
     */
    @Component
    private MavenProject mavenProject;

    /** 
     * 
     */
    @Component
    private ModelBuilder modelBuilder;

    /** 
     * 
     */
    @Component
    private ProjectBuilder projectBuilder;

    public void execute()
        throws MojoExecutionException
    {
        getLog().debug( "Generating BOM" );
        Model model = initializeModel();
        addDependencyManagement( model );
        try
        {
            writeModel( model );
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new MojoExecutionException( "Unable to write pom file.", e );
        }
    }

    private Model initializeModel()
    {
        Model pomModel = new Model();
        pomModel.setModelVersion( "4.0.0" );

        pomModel.setGroupId( bomGroupId );
        pomModel.setArtifactId( bomArtifactId );
        pomModel.setVersion( bomVersion );
        pomModel.setPackaging( "pom" );

        pomModel.setName( bomName );
        pomModel.setDescription( bomDescription );

        pomModel.getProperties().setProperty( "project.build.sourceEncoding", "UTF-8" );

        return pomModel;
    }

    private void addDependencyManagement( Model pomModel )
    {
        // Sort the artifacts for readability
        List<Artifact> projectArtifacts = new ArrayList<Artifact>( mavenProject.getArtifacts() );
        Collections.sort( projectArtifacts );

        DependencyManagement depMgmt = new DependencyManagement();
        for ( Artifact artifact : projectArtifacts )
        {
            Dependency dep = new Dependency();
            dep.setGroupId( artifact.getGroupId() );
            dep.setArtifactId( artifact.getArtifactId() );
            dep.setVersion( artifact.getVersion() );
            if ( !StringUtils.isEmpty( artifact.getClassifier() ))
            {
                dep.setClassifier( artifact.getClassifier() );
            }
            if ( !StringUtils.isEmpty( artifact.getType() ))
            {
                dep.setType( artifact.getType() );
            }
            if (exclusions != null) {
                applyExclusions(artifact, dep);
            }
            depMgmt.addDependency( dep );
        }
        pomModel.setDependencyManagement( depMgmt );
        getLog().debug( "Added " + projectArtifacts.size() + " dependencies." );
    }

    private void applyExclusions(Artifact artifact, Dependency dep) {
        for (BomExclusion exclusion : exclusions) {
            if (exclusion.getDependencyGroupId().equals(artifact.getGroupId()) &&
                    exclusion.getDependencyArtifactId().equals(artifact.getArtifactId())) {
                Exclusion ex = new Exclusion();
                ex.setGroupId(exclusion.getExclusionGroupId());
                ex.setArtifactId(exclusion.getExclusionArtifactId());
                dep.addExclusion(ex);
            }
        }
    }

    private void writeModel( Model pomModel )
        throws IOException
    {
        MavenXpp3Writer mavenWriter = new MavenXpp3Writer();

        File outputFile = new File( mavenProject.getBuild().getDirectory(), outputFilename );
        if ( !outputFile.getParentFile().exists() )
        {
            outputFile.getParentFile().mkdirs();
        }
        FileWriter writer = new FileWriter( outputFile );
        mavenWriter.write( writer, pomModel );
    }

}
