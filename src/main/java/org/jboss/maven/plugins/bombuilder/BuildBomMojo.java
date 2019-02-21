package org.jboss.maven.plugins.bombuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
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

import static org.codehaus.plexus.util.StringUtils.defaultString;
import static org.codehaus.plexus.util.StringUtils.trim;

/**
 * Build a BOM based on the dependencies in a GAV
 */
@Mojo( name = "build-bom", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE )
public class BuildBomMojo
    extends AbstractMojo
{

    private static final String VERSION_PROPERTY_PREFIX = "version.";
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
     * BOM name
     */
    @Parameter
    boolean addVersionProperties;

   /**
     * BOM description
     */
    @Parameter( defaultValue = "" )
    private String bomDescription;

    /**
     * BOM output file
     */
    @Parameter( defaultValue = "bom-pom.xml" )
    String outputFilename;

    /**
     * Whether the BOM should include the dependency exclusions that
     * are present in the source POM.  By default the exclusions
     * will not be copied to the new BOM.
     */
    @Parameter
    private List<BomExclusion> exclusions;

    /**
     * List of dependencies which should not be added to BOM
     */
    @Parameter
    private List<DependencyExclusion> dependencyExclusions;


    /**
     * Whether use properties to specify dependency versions in BOM
     */
    @Parameter
    boolean usePropertiesForVersion;

    /**
     * Whether to use dependency management dependencies.
     */
    @Parameter(defaultValue = "false")
    boolean useDependencyManagementDependencies;

    /**
     * Wheter to use dependencies.
     */
    @Parameter(defaultValue = "false")
    boolean useDependencies;

    /**
     * Wheter to use all resolved dependencies (defaults to true).
     */
    @Parameter(defaultValue = "true")
    boolean useAllResolvedDependencies = true;

    /**
     * The current project
     */
    @Component
    MavenProject mavenProject;

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

    private final PomDependencyVersionsTransformer versionsTransformer;
    private final ModelWriter modelWriter;
    
    public BuildBomMojo() {
        this(new ModelWriter(), new PomDependencyVersionsTransformer());
    }

    public BuildBomMojo(ModelWriter modelWriter, PomDependencyVersionsTransformer versionsTransformer) {
        this.versionsTransformer = versionsTransformer;
        this.modelWriter = modelWriter;
    }

    public void execute()
        throws MojoExecutionException
    {
        getLog().debug( "Generating BOM" );
        Model model = initializeModel();
        addDependencyManagement( model );
        if (usePropertiesForVersion) {
            model = versionsTransformer.transformPomModel(model);
            getLog().debug( "Dependencies versions converted to properties" );
        }
        modelWriter.writeModel(model, new File(mavenProject.getBuild().getDirectory(), outputFilename));
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

        pomModel.setProperties(new OrderedProperties());
        pomModel.getProperties().setProperty( "project.build.sourceEncoding", "UTF-8" );

        return pomModel;
    }

    private void addDependencyManagement( Model pomModel ) {
        List<Dependency> dependencies = new LinkedList<>();

        if (useAllResolvedDependencies) {
            dependencies.addAll(getArtifactsAsDependencies());
        }
        if (useDependencies) {
            dependencies.addAll(getDependencies());
        }
        if (useDependencyManagementDependencies) {
            dependencies.addAll(getDependencyManagementDependencies());
        }

        final DependencyManagement depMgmt = new DependencyManagement();
        dependencies.stream()
                .sorted(Comparator.comparing(Dependency::getGroupId).thenComparing(Dependency::getArtifactId))
                .forEach(depMgmt::addDependency);

        if (addVersionProperties) {
            Properties versionProperties = generateVersionProperties(dependencies);
            pomModel.getProperties().putAll(versionProperties);
        }

        pomModel.setDependencyManagement(depMgmt);
        getLog().debug( "Added " + dependencies.size() + " dependencies." );
    }

    private List<Dependency> getArtifactsAsDependencies() {
        return mavenProject.getArtifacts().stream()
                .map(this::map)
                .filter(this::isDependencyNotExcluded)
                .collect(Collectors.toList());
    }

    private List<Dependency> getDependencies() {
        return mavenProject.getDependencies().stream()
                .map(Dependency::clone)
                .filter(this::isDependencyNotExcluded)
                .collect(Collectors.toList());
    }

    private List<Dependency> getDependencyManagementDependencies() {
        final DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
        if (dependencyManagement == null) {
            return Collections.emptyList();
        }
        return dependencyManagement.getDependencies().stream()
                .map(Dependency::clone)
                .filter(this::isDependencyNotExcluded)
                .collect(Collectors.toList());
    }

    private Dependency map(Artifact artifact) {
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
        return dep;
    }

    boolean isDependencyNotExcluded(Dependency dependency) {
        if (dependencyExclusions == null || dependencyExclusions.size() == 0) {
            return true;
        }
        for (DependencyExclusion exclusion : dependencyExclusions) {
            if (matchesExcludedDependency(dependency, exclusion)) {
                getLog().debug( "Dependency " + dependency.getGroupId() + ":" + dependency.getArtifactId() + " matches excluded dependency " + exclusion.getGroupId() + ":" + exclusion.getArtifactId() );
                return false;
            }
        }
        return true;
    }

    boolean matchesExcludedDependency(Dependency dependency, DependencyExclusion exclusion) {
        String groupId = defaultAndTrim(dependency.getGroupId());
        String artifactId = defaultAndTrim(dependency.getArtifactId());
        String exclusionGroupId = defaultAndTrim(exclusion.getGroupId());
        String exclusionArtifactId = defaultAndTrim(exclusion.getArtifactId());
        boolean groupIdMatched = ("*".equals (exclusionGroupId) || groupId.equals(exclusionGroupId));
        boolean artifactIdMatched = ("*".equals (exclusionArtifactId) || artifactId.equals(exclusionArtifactId));
        return groupIdMatched && artifactIdMatched;
    }

    private String defaultAndTrim(String string) {
        return defaultString(trim(string), "");
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

    private Properties generateVersionProperties(List<Dependency> dependencies) {
        Properties versionProperties = new Properties();
        dependencies.forEach(dependency -> {
            String versionPropertyName = VERSION_PROPERTY_PREFIX + dependency.getGroupId();
            if (versionProperties.getProperty(versionPropertyName) != null
                    && !versionProperties.getProperty(versionPropertyName).equals(dependency.getVersion())) {
                versionPropertyName = VERSION_PROPERTY_PREFIX + dependency.getGroupId() + "." + dependency.getArtifactId();
            }
            versionProperties.setProperty(versionPropertyName, dependency.getVersion());
        });
        return versionProperties;
    }

    static class ModelWriter {

        void writeModel( Model pomModel, File outputFile )
            throws MojoExecutionException
        {
            if ( !outputFile.getParentFile().exists() )
            {
                outputFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter( outputFile )) {
                MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                mavenWriter.write(writer, pomModel);
            }
            catch ( IOException e )
            {
                e.printStackTrace();
                throw new MojoExecutionException( "Unable to write pom file.", e );
            }

        }
    }

}
