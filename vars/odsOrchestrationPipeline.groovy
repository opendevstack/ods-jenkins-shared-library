import java.nio.file.Paths
import java.lang.reflect.*
import java.lang.ClassLoader
import java.util.List
import java.util.concurrent.ConcurrentHashMap
import com.sun.beans.WeakCache
import com.sun.beans.TypeResolver
import java.lang.invoke.MethodType
import java.util.Vector 
import java.util.Iterator

import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.orchestration.usecase.OpenIssuesException
import org.ods.orchestration.InitStage
import org.ods.orchestration.BuildStage
import org.ods.orchestration.DeployStage
import org.ods.orchestration.TestStage
import org.ods.orchestration.ReleaseStage
import org.ods.orchestration.FinalizeStage
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.services.GitService
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps
import org.ods.util.UnirestConfig

@SuppressWarnings('AbcMetric')
def call(Map config) {
    def newName = "${env.JOB_NAME}/${env.BUILD_NUMBER}"
    UnirestConfig.init()
    def steps = new PipelineSteps(this)

    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)
  	logger.dumpCurrentStopwatchSize()
    def git = new GitService(steps, logger)

    def odsImageTag = config.odsImageTag
    if (!odsImageTag) {
        error "You must set 'odsImageTag' in the config map"
    }
    def versionedDevEnvsEnabled = config.get('versionedDevEnvs', false)
    def alwaysPullImage = !!config.get('alwaysPullImage', true)
    boolean startAgentEarly = config.get('startOrchestrationAgentOnInit', true)
    def startAgentStage = startAgentEarly ? MROPipelineUtil.PipelinePhases.INIT : null

    logger.debug ("Start agent stage: ${startAgentStage}")
    Project project = new Project(steps, logger)
    def repos = []

    logger.startClocked('orchestration-master-node')

  	try {
      node ('master') {
          logger.debugClocked('orchestration-master-node')
          // Clean workspace from previous runs
          [
              PipelineUtil.ARTIFACTS_BASE_DIR,
              PipelineUtil.SONARQUBE_BASE_DIR,
              PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR,
              MROPipelineUtil.REPOS_BASE_DIR,
          ].each { name ->
              logger.debug("Cleaning workspace directory '${name}' from previous runs")
              Paths.get(env.WORKSPACE, name).toFile().deleteDir()
          }

          logger.startClocked('pipeline-git-releasemanager')
          def scmBranches = scm.branches
          def branch = scmBranches[0]?.name
          if (branch && !branch.startsWith('*/')) {
              scmBranches = [[name: "*/${branch}"]]
          }

          // checkout local branch
          git.checkout(
              scmBranches,
              [[$class: 'LocalBranch', localBranch: '**']],
              scm.userRemoteConfigs,
              scm.doGenerateSubmoduleConfigurations
              )
          logger.debugClocked('pipeline-git-releasemanager')

          def envs = Project.getBuildEnvironment(steps, debug, versionedDevEnvsEnabled)

          logger.startClocked('pod-template')
          withPodTemplate(odsImageTag, steps, alwaysPullImage) {
              logger.debugClocked('pod-template')
              withEnv (envs) {
                def result
                def cannotContinueAsHasOpenIssuesInClosingRelease = false
                try {
                    result = new InitStage(this, project, repos, startAgentStage).execute()
                } catch (OpenIssuesException ex) {
                    cannotContinueAsHasOpenIssuesInClosingRelease = true
                }
                if (cannotContinueAsHasOpenIssuesInClosingRelease) {
                    logger.warn('Cannot continue as it has open issues in the release.')
                    return
                }
                if (result) {
                    project = result.project
                    repos = result.repos
                    if (!startAgentStage) {
                        startAgentStage = result.startAgent
                    }
                } else {
                    logger.warn('Skip pipeline as no project/repos computed')
                    return
                }

                new BuildStage(this, project, repos, startAgentStage).execute()

                new DeployStage(this, project, repos, startAgentStage).execute()

                new TestStage(this, project, repos, startAgentStage).execute()

                new ReleaseStage(this, project, repos).execute()

                new FinalizeStage(this, project, repos).execute()
              }
          }
      }
    } finally {
        logger.debug('-- SHUTTING DOWN RM (.. incl classloader HACK!!!!!) --')
        logger.resetStopwatch()
        project.clear()
        ServiceRegistry.removeInstance()
        UnirestConfig.shutdown()
        project = null
        git = null
        repos = null
        steps = null

        // HACK!!!!!
        GroovyClassLoader classloader = (GroovyClassLoader)this.class.getClassLoader()
        logger.debug("${classloader} - parent ${classloader.getParent()}")
        logger.debug("Currently loaded classes ${classloader.getLoadedClasses()}")
        // set the classloader name == run name
        try {
            logger.debug("Rename classloader name to this run ...")
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            Field loaderName = ClassLoader.class.getDeclaredField("name")
            loaderName.setAccessible(true);
            modifiersField.setInt(loaderName, loaderName.getModifiers() & ~Modifier.FINAL);
            loaderName.set(classloader, "" + newName)
        } catch (Exception e) {
            logger.debug("e: ${e}")
        }

        // unload GRAPES
        try {
            logger.debug("force grape unload")
            final Class<?> grape = 
               this.class.getClassLoader().loadClass('groovy.grape.Grape');
            Field instance = grape.getDeclaredField("instance")
            instance.setAccessible(true);

            Object grapeInstance = instance.get()

            Field loadedDeps = grapeInstance.class.getDeclaredField("loadedDeps")
            loadedDeps.setAccessible(true);
            def result = ((Map)loadedDeps.get(grapeInstance)).remove(
               this.class.getClassLoader())
            logger.debug ("removed graps loader: ${result}")
        } catch (Exception e) {
            logger.debug("cleanupGrapes err: ${e}")
        }

        /*
         * the remaining guys are:
         * a) java.lang.invoke.MethodType -> type references, such as com.vladsch.flexmark.parser.InlineParserFactory
         * b) (DONE) java.beans.ThreadGroupContext
         * c) (DONE) org.codehaus.groovy.ast.ClassHelper$ClassHelperCache 
         * d) (DONE) com.sun.beans.TypeResolver
         */
        // go thru this' class GroovyClassLoader -> URLClassLoader -> classes via reflection, and for each 
        // clear the above ...
        // unload GRAPES
        try {
            logger.debug("Unload other junk")
            Field classes = java.lang.ClassLoader.class.getDeclaredField("classes")
            classes.setAccessible(true);
            Iterator classV = ((Vector) classes.get(classloader)).iterator()

            // courtesy: https://github.com/jenkinsci/workflow-cps-plugin/blob/e034ae78cb28dcdbc20f24df7d905ea63d34937b/src/main/java/org/jenkinsci/plugins/workflow/cps/CpsFlowExecution.java#L1412
            Field classCacheF = Class.forName('org.codehaus.groovy.ast.ClassHelper$ClassHelperCache').getDeclaredField("classCache");
            classCacheF.setAccessible(true);
            Object classCache = classCacheF.get(null);
            logger.debug("UrlCL classes: ${classes.get(classloader)}")
            while (classV.hasNext()) {
                Class clazz = (Class)classV.next()
                // remove from ClassHelper$ClassHelperCache
                def removeCHC = classCache.getClass().getMethod("remove", Object.class).invoke(classCache, clazz);
                if (removeCHC) {
                    logger.debug ("removed class: ${clazz} from ${classCacheF}")
                }
            }
        } catch (Exception e) {
            logger.debug("cleanupJunk err: ${e}")
        }

        try {
            logger.debug("starting type-resolver (full) cleanup")
            // https://github.com/mjiderhamn/classloader-leak-prevention/issues/125
            final Class<?> typeResolverClass = 
                this.class.getClassLoader().loadClass('com.sun.beans.TypeResolver');

            if (typeResolverClass == null) { 
                logger.debug('could not find typresolver class')
                return; 
            } 

            Field modifiersField2 = Field.class.getDeclaredField("modifiers");
            modifiersField2.setAccessible(true);
            
            Field localCaches = typeResolverClass.getDeclaredField("CACHE")
            localCaches.setAccessible(true)
            modifiersField2.setInt(localCaches, localCaches.getModifiers() & ~Modifier.FINAL);

            WeakCache wCache = localCaches.get(null)
            wCache.clear()
        } catch (Exception e) {
            logger.debug("could not clean type-resolver: ${e}")
        }

        try {
            logger.debug("starting ThreadGroupContext cleanup")
            final Class<?> threadGroupContextClass = 
                this.class.getClassLoader().loadClass('java.lang.ThreadGroupContext');

            if (threadGroupContextClass == null) { 
                logger.debug('could not find threadGroupContextClass class')
                return; 
            } 
            
            Method contextMethod = threadGroupContextClass.getClass().getMethod("getContext")
            contextMethod.setAccessible(true)
            Object context = contextMethod.invoke(null, null);

            Method clearCacheMethod = context.getClass().getMethod("clearBeanInfoCache")
            clearCacheMethod.setAccessible(true)
            clearCacheMethod.invoke(null, null);
        } catch (Exception e) {
            logger.debug("could not clean ThreadGroupContext: ${e}")
        }

        // use the jenkins INTERNAL cleanupHeap method - attention NOTHING can happen after this method!
        try {
            logger.debug("forceClean via jenkins internals....")
            Method cleanupHeap = currentBuild.getRawBuild().getExecution().class.
                getDeclaredMethod("cleanUpHeap")
            cleanupHeap.setAccessible(true)
            cleanupHeap.invoke(currentBuild.getRawBuild().getExecution(), null)
        } catch (Exception e) {
            logger.debug("cleanupHeap err: ${e}")
        }
        classloader.close()
    }
}

protected void clearIfConcurrentHashMap(Object object, Logger logger) {
    if (!(object instanceof ConcurrentHashMap)) { return; }
    ConcurrentHashMap<?,?> map = (ConcurrentHashMap<?,?>) object;
    int nbOfEntries=map.size();
    map.clear();
    logger.info("Detected and fixed leak situation for java.io.ObjectStreamClass ("+nbOfEntries+" entries were flushed).");
}

@SuppressWarnings('GStringExpressionWithinString')
private withPodTemplate(String odsImageTag, IPipelineSteps steps, boolean alwaysPullImage, Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    def dockerRegistry = steps.env.DOCKER_REGISTRY ?: 'docker-registry.default.svc:5000'
    def podLabel = "mro-jenkins-agent-${env.BUILD_NUMBER}"
    def odsNamespace = env.ODS_NAMESPACE ?: 'ods'
    if (!OpenShiftService.envExists(steps, odsNamespace)) {
        logger.warn("Could not find ods namespace '${odsNamespace}' - defaulting to legacy namespace: 'cd'!\r" +
            "Please configure 'env.ODS_NAMESPACE' to point to the ODS Openshift namespace")
        odsNamespace = 'cd'
    }
    podTemplate(
        label: podLabel,
        cloud: 'openshift',
        containers: [
            containerTemplate(
                name: 'jnlp',
                image: "${dockerRegistry}/${odsNamespace}/jenkins-agent-base:${odsImageTag}",
                workingDir: '/tmp',
                resourceRequestMemory: '512Mi',
                resourceLimitMemory: '1Gi',
                resourceRequestCpu: '200m',
                resourceLimitCpu: '1',
                alwaysPullImage: "${alwaysPullImage}",
                args: '${computer.jnlpmac} ${computer.name}',
                envVars: []
            )
        ],
        volumes: [],
        serviceAccount: 'jenkins',
        idleMinutes: 10,
    ) {
        logger.startClocked('ods-mro-pipeline')
        try {
            block()
        } finally {
            logger.infoClocked('ods-mro-pipeline', '**** ENDED orchestration pipeline ****')
        }
    }
}

return this

