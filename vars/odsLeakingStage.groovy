import org.ods.services.ServiceRegistry
import org.ods.services.OpenShiftService

import org.ods.util.Logger
import org.ods.util.ILogger

import org.ods.orchestration.ThrowingStage

import java.lang.reflect.*
import java.lang.ClassLoader
import java.lang.Class
import groovy.grape.*

import java.util.*
import java.util.logging.*
import java.util.concurrent.ConcurrentHashMap

import org.ods.util.UnirestConfig

def call (Map config, Closure stages = null) {
    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)


    java.util.logging.Logger lLogger = 
        java.util.logging.Logger.getLogger('com.openhtmltopdf.config')
    java.util.logging.Handler[] lhandlers = lLogger.getHandlers()
    logger.debug("${lhandlers}")
    java.util.logging.Handler thisH = lhandlers.find { handler ->
        logger.debug("-> ${handler}, ${handler.class.getClassLoader()}, ${this.class.getClassLoader()}")
        handler.class.getClassLoader() == this.class.getClassLoader()
    }
    if (thisH) {
        lLogger.removeHandler(thisH)
    }

    logger.debug("current: ${currentBuild.getRawBuild()} ${currentBuild.getRawBuild().class} " + 
        "${currentBuild.getRawBuild().class.getClassLoader()}")

/*
    UnirestConfig.init()
    if (config.get('return', false)) {
        logger.debug '(imported) stage: log via logger'
        echo '(imported) stage: log via echo'
        return
    }
*/
    logger.debug('(root) odsLeakingStage debug')
    try {
        // node ('master') {
        /*    writeFile(
                file: "dummy.groovy",
                text: "@Library('ods-jenkins-shared-library@fix/extract_enums') _ \n" +
                    "echo '(imported) groovy: log directly from imported groovy' \n" + 
                    "odsLeakingStage(return : true) \n")
            def data = readFile (file: 'dummy.groovy')
            logger.debug "(root) Created dummy for script for dynamic load: \n ${data}"
            load ('dummy.groovy')
            ThrowingStage.logStatic(logger, '(root) static log')
        */
            new ThrowingStage(this).execute(stages)
        // }
    } finally {
        // clear it all ...
        logger.resetStopwatch()
        StringWriter writer = new StringWriter()
        currentBuild.getRawBuild().getLogText().writeLogTo(0, writer)        
        String log = writer.getBuffer().toString()
        currentBuild.result = 'FAILURE'
        ServiceRegistry.removeInstance()

        try {
            logger.debug("force grape stop")
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

        GroovyClassLoader classloader = (GroovyClassLoader)this.class.getClassLoader()
        try {
            logger.debug("Unload other junk")
            Field classes = java.lang.ClassLoader.class.getDeclaredField("classes")
            classes.setAccessible(true);
            Iterator classV = ((Vector) classes.get(classloader)).iterator()
            // courtesy: https://github.com/jenkinsci/workflow-cps-plugin/blob/e034ae78cb28dcdbc20f24df7d905ea63d34937b/src/main/java/org/jenkinsci/plugins/workflow/cps/CpsFlowExecution.java#L1412
            Field classCacheF = Class.forName('org.codehaus.groovy.ast.ClassHelper$ClassHelperCache').getDeclaredField("classCache");
            classCacheF.setAccessible(true);
            Object classCache = classCacheF.get(null);
            
            while (classV.hasNext()) {
                Class clazz = (Class)classV.next()
                def removed = classCache.getClass().getMethod("remove", Object.class).invoke(classCache, clazz);
                if (removed) {
                    logger.debug ("removed class: ${clazz} from ${classCacheF}")
                }
            }
        } catch (Exception e) {
            logger.debug("cleanupJunk err: ${e}")
        }

        try {
            logger.debug("starting ThreadGroupContext cleanup")
            final Class<?> threadGroupContextClass = 
                this.class.getClassLoader().loadClass('java.beans.ThreadGroupContext');

            if (threadGroupContextClass == null) { 
                logger.debug('could not find threadGroupContextClass class')
                return; 
            } 
            
            Method contextMethod = threadGroupContextClass.getDeclaredMethod("getContext")
            contextMethod.setAccessible(true)
            Object context = contextMethod.invoke(null, null);

            Method clearCacheMethod = context.getClass().getDeclaredMethod("clearBeanInfoCache")
            clearCacheMethod.setAccessible(true)
            clearCacheMethod.invoke(context, null);
        } catch (Exception e) {
            logger.debug("could not clean ThreadGroupContext: ${e}")
        }

        try {
            logger.debug(".....force cleanUpHeap")
            Method cleanupHeap = currentBuild.getRawBuild().getExecution().class.getDeclaredMethod("cleanUpHeap")
            cleanupHeap.setAccessible(true)
            cleanupHeap.invoke(currentBuild.getRawBuild().getExecution(), null)
        } catch (Exception e) {
            logger.debug("cleanupHeap err: ${e}")
        }
/*
        try {
            final Class<?> cacheClass = 
                this.class.getClassLoader().loadClass('java.io.ObjectStreamClass$Caches');

            if (cacheClass == null) { 
                logger.debug('could not find cache class')
                return; 
            } else {
                logger.debug("cache: ${cacheClass}")
            }

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            
            Field localDescs = cacheClass.getDeclaredField("localDescs")
            localDescs.setAccessible(true);
            modifiersField.setInt(localDescs, localDescs.getModifiers() & ~Modifier.FINAL);

            clearIfConcurrentHashMap(localDescs.get(null), logger);

            Field reflectors = cacheClass.getDeclaredField("reflectors")
            reflectors.setAccessible(true);
            modifiersField.setInt(reflectors, reflectors.getModifiers() & ~Modifier.FINAL);

            clearIfConcurrentHashMap(reflectors.get(null), logger);
        }
        catch (Exception e) {
            logger.debug("${e}")
        }
*/
    }
}

protected void clearIfConcurrentHashMap(Object object, Logger logger) {
    if (!(object instanceof ConcurrentHashMap)) { return; }
    ConcurrentHashMap<?,?> map = (ConcurrentHashMap<?,?>) object;
    int nbOfEntries=map.size();
    map.clear();
    logger.info("Detected and fixed leak situation for java.io.ObjectStreamClass ("+nbOfEntries+" entries were flushed).");
}

return this