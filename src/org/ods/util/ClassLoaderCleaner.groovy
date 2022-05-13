package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import com.sun.beans.WeakCache

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

@SuppressWarnings(['SystemOutPrint'])
class ClassLoaderCleaner {

    void clean(ILogger logger, String processId){
        logger.debug('-- Clean classloader --')

        GroovyClassLoader classloader = (GroovyClassLoader)this.class.getClassLoader()
        logger.debug("${classloader} - parent ${classloader.getParent()}")
        logger.debug("Currently loaded classes ${classloader.getLoadedClasses().size()}")

        logger.debug("Rename classloader name to this run ...")
        renameClassLoader(classloader, processId)

        logger.debug("force grape unload")
        unloadGrapes(classloader)

        logger.debug("unloadCache")
        unloadCache(classloader)

        logger.debug("starting type-resolver (full) cleanup")
        typeResolverCleanup(classloader)

        logger.debug("starting ThreadGroupContext cleanup")
        threadGroupContextCleanUp(classloader)

        logger.debug("Removing logger ...")
        removeLogger()

        classloader.close()
    }

    @NonCPS
    private void renameClassLoader(GroovyClassLoader classloader, String processId) {
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.setAccessible(true)
        Field loaderName = ClassLoader.class.getDeclaredField("name")
        loaderName.setAccessible(true)
        modifiersField.setInt(loaderName, loaderName.getModifiers() & ~Modifier.FINAL)
        loaderName.set(classloader, processId)
    }

    @NonCPS
    private void unloadGrapes(GroovyClassLoader classloader) {
        Class<?> grape = classloader.loadClass('groovy.grape.Grape')
        Field instance = grape.getDeclaredField("instance")
        instance.setAccessible(true)
        def grapeInstance = instance.get()
        Field loadedDeps = grapeInstance.class.getDeclaredField("loadedDeps")
        loadedDeps.setAccessible(true)
        loadedDeps.get(grapeInstance).remove(this.class.getClassLoader())
    }

    @NonCPS
    private void unloadCache(GroovyClassLoader classloader) {
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
        Field classes = ClassLoader.class.getDeclaredField("classes")
        classes.setAccessible(true)
        Iterator classV = ((List) classes.get(classloader)).iterator()

        // courtesy: https://github.com/jenkinsci/workflow-cps-plugin/
        // blob/e034ae78cb28dcdbc20f24df7d905ea63d34937b/src/main/java/
        // org/jenkinsci/plugins/workflow/cps/CpsFlowExecution.java#L1412
        Field classCacheF = this.class.getClassLoader()
            .loadClass('org.codehaus.groovy.ast.ClassHelper$ClassHelperCache')
            .getDeclaredField("classCache")
        classCacheF.setAccessible(true)
        Object classCache = classCacheF.get(null)
        while (classV.hasNext()) {
            Class clazz = (Class) classV.next()
            classCache.getClass().getMethod("remove", Object.class).invoke(classCache, clazz)
        }
    }

    @NonCPS
    private void threadGroupContextCleanUp(GroovyClassLoader classloader) {
        Class<?> threadGroupContextClass = classloader.loadClass('java.beans.ThreadGroupContext')
        if (threadGroupContextClass == null) {
            System.out.println("could not find threadGroupContextClass class")
            return
        }

        Method contextMethod = threadGroupContextClass.getDeclaredMethod("getContext")
        contextMethod.setAccessible(true)
        Object context = contextMethod.invoke(null, null)

        Method clearCacheMethod = context.getClass().getDeclaredMethod("clearBeanInfoCache")
        clearCacheMethod.setAccessible(true)
        clearCacheMethod.invoke(context, null)
    }

    @NonCPS
    private void typeResolverCleanup(GroovyClassLoader classloader) {
        // https://github.com/mjiderhamn/classloader-leak-prevention/issues/125
        Class<?> typeResolverClass = classloader.loadClass('com.sun.beans.TypeResolver')
        if (typeResolverClass == null) {
            System.out.println("could not find com.sun.beans.TypeResolver class")
            return
        }

        Field modifiersField2 = Field.class.getDeclaredField("modifiers")
        modifiersField2.setAccessible(true)

        Field localCaches = typeResolverClass.getDeclaredField("CACHE")
        localCaches.setAccessible(true)
        modifiersField2.setInt(localCaches, localCaches.getModifiers() & ~Modifier.FINAL)

        WeakCache wCache = localCaches.get(null)
        wCache.clear()
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

