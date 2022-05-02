package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import com.sun.beans.WeakCache

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class ClassLoaderCleaner {

    private final ILogger logger

    ClassLoaderCleaner(ILogger logger){
        this.logger = logger
    }

    def clean(processId){
        logger.debug('-- SHUTTING DOWN RM (.. incl classloader HACK!!!!!) --')

        // HACK!!!!!
        GroovyClassLoader classloader = (GroovyClassLoader)this.class.getClassLoader()
        logger.debug("${classloader} - parent ${classloader.getParent()}")
        logger.debug("Currently loaded classes ${classloader.getLoadedClasses()}")
        // set the classloader name == run name
        try {
            logger.debug("Rename classloader name to this run ...")
            Field modifiersField = Field.class.getDeclaredField("modifiers")
            modifiersField.setAccessible(true)
            Field loaderName = ClassLoader.class.getDeclaredField("name")
            loaderName.setAccessible(true)
            modifiersField.setInt(loaderName, loaderName.getModifiers() & ~Modifier.FINAL)
            loaderName.set(classloader, "" + processId)
        } catch (Exception e) {
            logger.debug("e: ${e}")
        }

        // unload GRAPES
        try {
            logger.debug("force grape unload")
            final Class<?> grape = this.class.getClassLoader().loadClass('groovy.grape.Grape')
            Field instance = grape.getDeclaredField("instance")
            instance.setAccessible(true)

            Object grapeInstance = instance.get()

            Field loadedDeps = grapeInstance.class.getDeclaredField("loadedDeps")
            loadedDeps.setAccessible(true)
            def result = ((Map)loadedDeps.get(grapeInstance)).remove(this.class.getClassLoader())
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
            classes.setAccessible(true)
            Iterator classV = ((Vector) classes.get(classloader)).iterator()

            // courtesy: https://github.com/jenkinsci/workflow-cps-plugin/
            // blob/e034ae78cb28dcdbc20f24df7d905ea63d34937b/src/main/java/
            // org/jenkinsci/plugins/workflow/cps/CpsFlowExecution.java#L1412
            Field classCacheF = Class.forName('org.codehaus.groovy.ast.ClassHelper$ClassHelperCache')
                .getDeclaredField("classCache")
            classCacheF.setAccessible(true)
            Object classCache = classCacheF.get(null)
            logger.debug("UrlCL classes: ${classes.get(classloader)}")
            while (classV.hasNext()) {
                Class clazz = (Class)classV.next()
                // remove from ClassHelper$ClassHelperCache
                def removeCHC = classCache.getClass().getMethod("remove", Object.class).invoke(classCache, clazz)
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
                this.class.getClassLoader().loadClass('com.sun.beans.TypeResolver')

            if (typeResolverClass == null) {
                logger.debug('could not find typresolver class')
                return
            }

            Field modifiersField2 = Field.class.getDeclaredField("modifiers")
            modifiersField2.setAccessible(true)

            Field localCaches = typeResolverClass.getDeclaredField("CACHE")
            localCaches.setAccessible(true)
            modifiersField2.setInt(localCaches, localCaches.getModifiers() & ~Modifier.FINAL)

            WeakCache wCache = localCaches.get(null)
            wCache.clear()
        } catch (Exception e) {
            logger.debug("could not clean type-resolver: ${e}")
        }

        try {
            logger.debug("starting ThreadGroupContext cleanup")
            final Class<?> threadGroupContextClass =
                this.class.getClassLoader().loadClass('java.beans.ThreadGroupContext')

            if (threadGroupContextClass == null) {
                logger.debug('could not find threadGroupContextClass class')
                return
            }

            Method contextMethod = threadGroupContextClass.getDeclaredMethod("getContext")
            contextMethod.setAccessible(true)
            Object context = contextMethod.invoke(null, null)

            Method clearCacheMethod = context.getClass().getDeclaredMethod("clearBeanInfoCache")
            clearCacheMethod.setAccessible(true)
            clearCacheMethod.invoke(context, null)
        } catch (Exception e) {
            logger.debug("could not clean ThreadGroupContext: ${e}")
        }

        logger.debug("Removing logger ...")
        removeLogger ()
    }

    @NonCPS
    void removeLogger () {
        java.util.logging.Logger lLogger =
            java.util.logging.Logger.getLogger('com.openhtmltopdf.config')
        java.util.logging.Handler[] lhandlers = lLogger.getHandlers()
        java.util.logging.Handler thisH = lhandlers.find { handler ->
            System.out.println("-> handler: " + handler.getFormatter() +
                " -cl: " + handler.getFormatter().class.getClassLoader() +
                " -this cl: " + this.class.getClassLoader())
            handler.getFormatter().class.getClassLoader() == this.class.getClassLoader()
        }
        if (thisH) {
            System.out.println("-> removed: " + thisH)
            lLogger.removeHandler(thisH)
        }
    }

}

